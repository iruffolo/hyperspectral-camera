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


class ThePlot():
    def __init__(self, fn):
        self.titles = ("Raw", "RGB", "Gray", "UF + Moving Avg", "Mask", "Labels")
        self.rows = 1
        self.cols = len(self.titles)

        self.fig, self.ax = plt.subplots(self.rows, self.cols, figsize=(25, 5))
        self.fig.suptitle(f"{fn}", fontsize=25)

        self.curr = 0

    def add_subplot(self, img, cmap=None):
        ax = self.ax[self.curr]
        ax.imshow(img, cmap=cmap)
        ax.set_title(self.titles[self.curr])

        self.curr += 1

    def vlines(self, mids, ymin=0, ymax=4000):
        for ax in self.ax[:-1]:
            ax.vlines(mids, ymin, ymax)

    def plot(self, x, y=None):
        self.ax[-1].plot(x)

    def show(self):
        plt.tight_layout()
        plt.show()


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


def rolling_avg(img, w=2, fw=1):

    img = ndimage.uniform_filter(img, size=(fw, img.shape[1]), mode='wrap')

    stacking = np.array([np.roll(img, x, axis=0) for x in range(-w, w+1)])
    roll = np.mean(stacking, axis=0)

    return roll


def consecutive(x, stepsize=5):
    return np.split(x, np.where(np.diff(x) > stepsize)[0]+1)


def find_mids(source):
    print("Finding mids")

    # Get statistics
    avg_row = np.mean(source, axis=1)
    std_row = np.std(source, axis=1)
    stacking = np.array([np.roll(avg_row, x) for x in range(-2, 3)])
    avg_cols = np.mean(stacking, axis=0)
    std_cols = np.std(stacking, axis=0)

    # Array for labelling rows
    mask_black = np.zeros(gray.shape[0])

    # search_grid = [x/1000 for x in range(230, 300, 1)]
    # print(search_grid)

    r_threshold = 0.59
    cond = (avg_row < r_threshold) & (std_cols < 0.005)
    masks = np.where(cond)[0]
    mask_groups = consecutive(masks)
    print(masks)
    print(mask_groups)
    np.put(mask_black, masks, 1)

    # Debug
    for i, x in enumerate(avg_row):
        print(i, x, avg_cols[i], std_row[i], std_cols[i])

    mask_img_black = np.repeat(mask_black[:, np.newaxis], raw.shape[1], axis=1)
    fig.add_subplot(mask_img_black.T, cmap='gray')

    mask_groups = consecutive(masks)
    print(mask_groups)
    # Drop smallest groups
    if (len(mask_groups) > 3):
        mask_groups.sort(key=lambda x: len(x), reverse=True)
        mask_groups = mask_groups[:3]
    # Resort in order
    mask_groups.sort(key=lambda x: np.mean(x))

    # Find middle row in group
    mids = np.array([int((np.max(x)-np.min(x))/2) + np.min(x)
                     for x in mask_groups])
    print(f"mids: {mids}")
    print(f"mids diff: {np.diff(mids)}")

    final_mids = list()
    diff = np.diff(mids)
    for i in range(len(diff)):
        if diff[i] >= 1050 and diff[i] <= 1250:
            if mids[i] not in final_mids:
                final_mids.append(mids[i])
            if mids[i+1] not in final_mids:
                final_mids.append(mids[i+1])

    return final_mids


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

    fn = sorted_files[3]
    dng, raw = readDNG(dir + fn)

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

    fig = ThePlot(fn)

    # Processed images
    lin = linearize(dng, raw)**(1/100)
    lin_a = rolling_avg(lin, w=1, fw=15)
    gray = color.rgb2gray(rgb)
    a = rolling_avg(gray)

    # Plot all
    fig.add_subplot(lin.T, cmap='gray')
    fig.add_subplot(np.transpose(rgb, axes=(1, 0, 2)))
    fig.add_subplot(gray.T, cmap='gray')
    fig.add_subplot(a.T, cmap='gray')

    mids = find_mids(lin_a)

    row_labels, one_hot_labels = calc_labels(4000,
                             np.array(mids),
                             int(params[fn]['ton']),
                             int(params[fn]['toff']),
                             int(params[fn]['black_mul']))
    print(row_labels)
    print(row_labels.shape)

    np.set_printoptions(threshold = 10000)
    print(np.where(np.sum(one_hot_labels, axis=1) == 0))

    fig.plot(row_labels)

    fig.plot(np.mean(one_hot_labels, axis=1))

    fig.vlines(mids)
    fig.show()
