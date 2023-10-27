#usr/bin/python

import cv2
import os
import numpy as np
import matplotlib
matplotlib.use('TkAgg')
import matplotlib.pyplot as plt
import rawpy
from rawpy import FBDDNoiseReductionMode
from skimage import color


pattern = [1,2,3,4,5,6]


def readDNG(filename):
    dng_path = f"{os.getcwd()}/data/{filename}"
    dng = rawpy.imread(dng_path)
    raw_image = dng.raw_image.astype(int)
    return dng, raw_image
    
# Linearize image
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
        print(i,x)
    # for i, x in enumerate(rollingAvg(avg)):
    #     print(i,x)

    print(f"Min: {np.min(avg)}")
    print(f"Max: {np.max(avg)}")
    print(f"Std: {np.std(avg)}")
    print(f"Avg: {np.mean(avg)}")


def denoise(img):
    dst = cv2.fastNlMeansDenoising(img, None, 5, 7, 21)
    return dst

def rollingAvg(a, n=5):
    ret = np.cumsum(a, dtype=float)
    ret[n:] = ret[n:] - ret[:-n]
    return ret[n - 1:] / n


if __name__=="__main__":

    # np.set_printoptions(threshold = np.inf)

    fn = "LBL_5_0_15_0_2.dng"
    dng, raw = readDNG(fn)

    # demosaicing by rawpy
    rgb = dng.postprocess(gamma=(2.222,4.5), 
                          no_auto_bright=False,
                          fbdd_noise_reduction=FBDDNoiseReductionMode.Full,
                          use_camera_wb=True,
                          use_auto_wb=True)

    fig = plt.figure(figsize=(50,10))

    row=1
    col=6

    titles=("RGB", "Red", "Green", "Blue", "Mask (White)", "Mask (Black)")

    fig.add_subplot(row,col,1)
    plt.title(titles[0])
    plt.imshow(rgb)


    for i in range(1,4):
        fig.add_subplot(row,col,i+1)
        plt.title(titles[i])
        plt.imshow(rgb[:,:,i-1])

    # Cut edges off rows
    cols = rgb.shape[1]
    uslice = int(cols - cols/4)
    lslice = int(cols - cols*3/4)
    avg_channel = np.mean(rgb[:,lslice:uslice,:], axis=1)


    # Array for labelling rows
    mask_black = np.zeros(rgb.shape[0])
    mask_white = np.zeros(rgb.shape[0])

    sum = np.sum(avg_channel, axis=1)
    diff = np.max(avg_channel, axis=1) - np.min(avg_channel, axis=1)
    std = np.std(avg_channel, axis=1)

    np.put(mask_white, np.where((sum > 120) & (diff < 5) & (sum/diff > 40) & (std < 6)), 1)
    np.put(mask_black, np.where((sum < 200) & (diff < 40) & (sum/diff < 10)), 1)

    for i,x in enumerate(sum):
        print(i, avg_channel[i], x, diff[i], x/diff[i], np.std(avg_channel[i]))

    a = rollingAvg(rgb)

    mask_img_white = np.repeat(mask_white[:, np.newaxis], raw.shape[1], axis=1)
    fig.add_subplot(row,col,5)
    plt.title(titles[4])
    plt.imshow(mask_img_white, cmap="gray")
    mask_img_black = np.repeat(mask_black[:, np.newaxis], raw.shape[1], axis=1)
    fig.add_subplot(row,col,6)
    plt.title(titles[5])
    plt.imshow(mask_img_black, cmap="gray")
    plt.show()

    printStats(rgb)

