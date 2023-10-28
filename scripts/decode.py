#!usr/bin/python

import matplotlib.pyplot as plt
import rawpy
import cv2
import os
import numpy as np
import matplotlib
from rawpy import FBDDNoiseReductionMode
from skimage import color
# from scipy import ndimage
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


def rollingAvg(img, w=4, fw=1):

    # roll = ndimage.uniform_filter(img, size=(fw, img.shape[1]), mode='wrap')

    roll = np.zeros_like(img)

    for i in range(0, roll.shape[0]):
        x = np.zeros_like(img[0, :])

        for j in range(-w, w+1):
            idx = i+j
            if (idx < 0 or idx > roll.shape[0]-1):
                roll[i, :] = np.ones_like(img[0, :])
                break
            else:
                x = np.stack((x, img[idx, :]), axis=0)
                x = np.sum(x, axis=0)
            roll[i, :] = x / ((w*2) + 1)

    # roll = ndimage.uniform_filter(roll,
    #                             size=(fw, roll.shape[1]), mode='wrap')
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

    fn = sorted_files[8]
    dng, raw = readDNG(dir + fn)

    # lin = linearize(dng, raw)

    # demosaicing by rawpy
    rgb = dng.postprocess(gamma=(2.22, 4.5),
                          no_auto_bright=False,
                          fbdd_noise_reduction=FBDDNoiseReductionMode.Full,
                          use_camera_wb=True,
                          user_black=dng.black_level_per_channel[0],
                          output_bps=8)

    fig = plt.figure(figsize=(25, 5))
    fig.suptitle(f"{fn}", fontsize=25)

    row = 1
    col = 7
    titles = ("Red", "Green", "Blue", "RGB", "Gray",
              "Uniform Filter + Moving Avg", "Mask (Black)")

    for i in range(0, 3):
        fig.add_subplot(row, col, i+1)
        plt.title(titles[i])
        plt.imshow(rgb[:, :, i])

    fig.add_subplot(row, col, 4)
    plt.title(titles[3])
    plt.imshow(rgb)

    gray = color.rgb2gray(rgb)
    fig.add_subplot(row, col, 5)
    plt.title(titles[4])
    plt.imshow(gray, cmap='gray')

    # # Cut edges off rows
    # cols = rgb.shape[1]
    # uslice = int(cols - cols/4)
    # lslice = int(cols - cols*3/4)
    # a = rollingAvg(gray[:,lslice:uslice])
    a = rollingAvg(gray)

    fig.add_subplot(row, col, 6)
    plt.title(titles[5])
    plt.imshow(a, cmap='gray')

    avg_gray = np.mean(a, axis=1)
    std_gray = np.std(a, axis=1)
    # avg_channel = np.mean(gray, axis=1)
    # avg = np.mean(avg_channel, axis=1)
    # sum = np.sum(avg_channel, axis=1)
    # diff = np.max(avg_channel, axis=1) - np.min(avg_channel, axis=1)
    # std = np.mean(np.std(a, axis=1), axis=1)

    # Array for labelling rows
    mask_black = np.zeros(gray.shape[0])

    # mask = np.where((avg < 50) & (sum < 150) & (diff < 35))
    mask = np.where((avg_gray < 0.20) & (std_gray < 0.1))
    np.put(mask_black, mask, 1)

    # Debug
    # for i,x in enumerate(sum):
    #     print(i, avg_channel[i], avg[i], x, diff[i], std[i])
    for i, x in enumerate(avg_gray):
        print(i, x, std_gray[i])

    mask_img_black = np.repeat(mask_black[:, np.newaxis], raw.shape[1], axis=1)
    fig.add_subplot(row, col, 7)
    plt.title(titles[6])
    plt.imshow(mask_img_black, cmap='gray')
    plt.tight_layout()
    plt.show()

    row_labels = calc_labels(mask_black,
                             float(params[fn]['ton']),
                             float(params[fn]['toff']),
                             float(params[fn]['black_mul']))
    print(row_labels)
