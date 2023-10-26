#usr/bin/python

import cv2
import os
import numpy as np
import matplotlib.pyplot as plt
import rawpy

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
    np.set_printoptions(threshold = np.inf)

    avg = np.mean(image, axis=ax)
    print(f"Min: {np.min(avg)}")
    print(f"Max: {np.max(avg)}")
    print(f"Std: {np.std(avg)}")
    print(f"Avg: {np.mean(avg)}")


def denoise(img):
    dst = cv2.fastNlMeansDenoising(img, None, 5, 7, 21)
    return dst


if __name__=="__main__":

    fn = "LBL_100_0.dng"
    dng, raw = readDNG(fn)

    printStats(raw)
    printImage(raw**(1/2.2))

    rgb = dng.postprocess()  # demosaicing by rawpy
    printStats(rgb, ax=(1,2))
    plt.imshow(rgb)
    plt.show()

    lin = linearize(dng, raw)
    print(lin.shape)
    printStats(lin)
    plt.imshow(lin)
    plt.show()

    raw_8bit = np.uint8(lin)
    dn = denoise(raw_8bit)
    printImage(dn)
    printStats(dn)

    
