#!usr/bin/python

import matplotlib.pyplot as plt
import rawpy
import cv2
import os
import numpy as np
import matplotlib
from rawpy import FBDDNoiseReductionMode
from skimage import color
from scipy import ndimage
from label_rows import calc_labels
matplotlib.use('TkAgg')


def readDNG(filename):
    dng = rawpy.imread(filename)
    raw_image = dng.raw_image.astype(int)
    return dng, raw_image


def linearize(dng, raw):
    black = dng.black_level_per_channel[0]
    saturation = dng.white_level
    raw -= black
    uint10_max = 2**10 - 1
    raw = raw * int(uint10_max/(saturation - black))
    raw = np.clip(raw, 0, uint10_max)
    return raw


def printImage(image):
    plt.imshow(image, cmap='gray')
    plt.show()


def printStats(image, ax=0):

    avg = np.mean(image, axis=ax)

    for i, x in enumerate(avg):
        print(i, x)
    # for i, x in enumerate(rollingAvg(avg)):
    #     print(i,x)

    print(f"Min: {np.min(avg)}")
    print(f"Max: {np.max(avg)}")
    print(f"Std: {np.std(avg)}")
    print(f"Avg: {np.mean(avg)}")


def denoise(img):
    dst = cv2.fastNlMeansDenoising(img, None, 5, 7, 21)
    return dst


def rollingAvg(img, w=2, fw=1):

    img = ndimage.uniform_filter(img, size=(fw, img.shape[1]), mode='wrap')

    stacking = np.array([np.roll(img, x, axis=0) for x in range(-w, w+1)])
    roll = np.mean(stacking, axis=0)

    return roll


if __name__ == "__main__":

    # np.set_printoptions(threshold = np.inf)
    dir = f"{os.getcwd()}/data_cc/"
    files = os.listdir(dir)

    params = dict()
    map = ['scenario', 'timestamp', 'mode', 'frame_num', 'ton',
           'toff', 'black_mul', 'mux', 'exposure_time', 'iso']

    for f in files:
        parameters = f.split('_')
        params[f] = dict()
        for i, p in enumerate(parameters):
            params[f][map[i]] = p

    sorted_files = sorted(params,
                          key=lambda x: params[x]['timestamp'])

    print(sorted_files)
    fn = sorted_files[1]
    dng, raw = readDNG(dir + fn)

    # lin = linearize(dng, raw)

    # demosaicing by rawpy
    rgb = dng.postprocess(gamma=(100, 4.5),
                          no_auto_bright=False,
                          fbdd_noise_reduction=FBDDNoiseReductionMode.Full,
                          use_camera_wb=False,
                          use_auto_wb=True,
                          user_black=dng.black_level_per_channel[0],
                          dcb_enhance=True,
                          dcb_iterations=5,
                          output_bps=8)

    fig = plt.figure(figsize=(25, 5))
    fig.suptitle(f"{fn}", fontsize=25)

    titles = ("Raw", "RGB", "Gray", "UF + Moving Avg", "Mask")
    row = 1
    col = len(titles)

    # RAW
    fig.add_subplot(row, col, 1)
    plt.title(titles[0])
    lin = linearize(dng, raw)**(1/100)
    lin_a = rollingAvg(lin, w=1, fw=15)

    source = lin_a
    avg_row = np.mean(source, axis=1)
    std_row = np.std(source, axis=1)
    stacking = np.array([np.roll(avg_row, x) for x in range(-2, 3)])
    avg_cols = np.mean(stacking, axis=0)
    std_cols = np.std(stacking, axis=0)

    plt.imshow(lin_a.T, cmap='gray')
    plt.plot(-1*(avg_row+1)**11, alpha=0.4)

    fig.add_subplot(row, col, 2)
    plt.title(titles[1])
    plt.imshow(np.transpose(rgb, axes=(1, 0, 2)))
    plt.plot(-1*(avg_row+1)**11, alpha=0.4)

    gray = color.rgb2gray(rgb)
    fig.add_subplot(row, col, 3)
    plt.title(titles[2])
    plt.imshow(gray.T, cmap='gray')
    plt.plot(-1*(avg_row+1)**11, alpha=0.4)

    a = rollingAvg(gray)
    fig.add_subplot(row, col, 4)
    plt.title(titles[3])
    plt.imshow(a.T, cmap='gray')
    plt.plot(-1*(avg_row+1)**11, alpha=0.4)

    # Array for labelling rows
    mask_black = np.zeros(gray.shape[0])

    # mask = np.where((avg < 50) & (sum < 150) & (diff < 35))
    search_grid = [x/1000 for x in range(230, 300, 1)]
    print(search_grid)

    # for threshold in search_grid:
    #     cond = (avg_row < threshold) & (avg_cols < threshold) & (std_cols < 0.01)
    #     masks = consecutive(np.where(cond)[0])

    # black_rows = np.where(np.roll(mask, 1) - mask)[0]
    # print(black_rows)
    # if (black_rows > 1):
    #     mid = [int((black_rows[i] + black_rows[i+1])/2)
    #            for i in range(0, black_rows.size, 2)]
    #     print(mid)

    def consecutive(x, stepsize=5):
        return np.split(x, np.where(np.diff(x) > stepsize)[0]+1)

    r_threshold = 0.58
    cond = (avg_row < r_threshold) & (std_cols < 0.005)
    masks = np.where(cond)[0]
    mask_groups = consecutive(masks)
    print(masks)
    print(mask_groups)
    np.put(mask_black, masks, 1)

    # Debug
    # for i,x in enumerate(sum):
    #     print(i, avg_channel[i], avg[i], x, diff[i], std[i])
    for i, x in enumerate(avg_row):
        print(i, x, avg_cols[i], std_row[i], std_cols[i])

    mask_img_black = np.repeat(mask_black[:, np.newaxis], raw.shape[1], axis=1)
    fig.add_subplot(row, col, 5)
    plt.title(titles[4])
    plt.imshow(mask_img_black.T, cmap='gray')
    plt.plot(-1*(avg_row+1)**11, alpha=0.4)

    mask_groups = consecutive(masks)
    print(mask_groups)
    # Drop smallest groups
    if (len(mask_groups) > 3):
        mask_groups.sort(key=lambda x: len(x), reverse=True)
        mask_groups = mask_groups[:3]
    # Resort in order
    mask_groups.sort(key=lambda x: sum(x))

    # Find middle row in group
    mids = np.array([int((np.max(x)-np.min(x))/2) + np.min(x)
                     for x in mask_groups])

    # black_rows = [np.array([x - 6, x + 6]) for x in mids]
    # print(black_rows)

    row_labels = calc_labels(mask_black.shape[0],
                             mids,
                             float(params[fn]['ton']),
                             float(params[fn]['toff']),
                             float(params[fn]['black_mul']))
    print(row_labels)
    print(row_labels.shape)

    plt.tight_layout()
    # plt.show()

