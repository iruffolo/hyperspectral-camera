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
from scipy import ndimage


pattern = [1,2,3,4,5,6]


def readDNG(filename):
    dng = rawpy.imread(filename)
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

def rollingAvg(x, w=5):

    return ndimage.uniform_filter(x, size=(w,x.shape[1],1), mode='constant')
    # return np.convolve(x, np.ones(w), 'valid') / w

    roll = np.zeros((a.shape[0]-2*n, a.shape[1], a.shape[2]))

    for i in range(0, roll.shape[0]):
        x = a[i+2,:,:] + a[i+1,:,:] + a[i,:,:]
        roll[i,:,:] = x
        print(i,n)


if __name__=="__main__":

    # np.set_printoptions(threshold = np.inf)
    dir = f"{os.getcwd()}/data_cc/"

    files = os.listdir(dir)
    fn = dir + files[9]

    params = dict()

    map = ['scenario', 'timestamp', 'mode', 'frame_num', 'ton', 
           'toff', 'black_mul', 'mux', 'exposure_time', 'iso']

    for f in files:
        parameters = f.split('_')
        params[f] = dict()
        for i,p in enumerate(parameters):
            params[f][map[i]] = p

    dng, raw = readDNG(fn)

    # lin = linearize(dng, raw)

    # demosaicing by rawpy
    rgb = dng.postprocess(gamma=(2.222,4.5), 
                          no_auto_bright=False,
                          fbdd_noise_reduction=FBDDNoiseReductionMode.Full,
                          use_camera_wb=True,
                          user_black=dng.black_level_per_channel[0])

    fig = plt.figure(figsize=(40,10))

    row = 1 
    col = 6
    titles=("Red", "Green", "Blue", "RGB", "Moving Avg", "Mask (Black)")

    for i in range(0,3):
        fig.add_subplot(row,col,i+1)
        plt.title(titles[i])
        plt.imshow(rgb[:,:,i])

    fig.add_subplot(row,col,4)
    plt.title(titles[3])
    plt.imshow(rgb)

    # Cut edges off rows
    # cols = rgb.shape[1]
    # uslice = int(cols - cols/4)
    # lslice = int(cols - cols*3/4)
    # avg_channel = np.mean(rgb[:,lslice:uslice,:], axis=1)
    a = rollingAvg(rgb)
    fig.add_subplot(row,col,5)
    plt.title(titles[4])
    plt.imshow(a)

    avg_channel = np.mean(a, axis=1)

    # Array for labelling rows
    mask_black = np.zeros(rgb.shape[0])

    sum = np.sum(avg_channel, axis=1)
    diff = np.max(avg_channel, axis=1) - np.min(avg_channel, axis=1)
    std = np.std(a, axis=1)

    np.put(mask_black, np.where((sum < 109) & (diff < 30) & (np.mean(std, axis=1) < 8)), 1)

    for i,x in enumerate(sum):
        print(i, avg_channel[i], x, diff[i], np.mean(std[i]))


    mask_img_black = np.repeat(mask_black[:, np.newaxis], raw.shape[1], axis=1)
    fig.add_subplot(row,col,6)
    plt.title(titles[5])
    plt.imshow(mask_img_black, cmap="gray")
    plt.tight_layout()
    plt.show()

