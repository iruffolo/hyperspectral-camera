#usr/bin/python

import cv2
import os
import numpy as np
import matplotlib.pyplot as plt
import rawpy
from process_raw import DngFile

def readDNG(filename):
    dng_path = f"{os.getcwd()}/data/{filename}"
    dng = rawpy.imread(dng_path)
    raw_image = dng.raw_image.astype(int)
    return dng, raw_image
    
def printImage(image, dpi=200):
    figure(dpi=dpi)
    plt.imshow(image, cmap='gray')
    plt.show()

if __name__=="__main__":

    fn = "LBL_100_0.dng"
    dng, img = readDNG(fn)

    # raw = dng.raw  # np.uint16
    # raw_8bit = np.uint8(raw >> (dng.bit-8))

    print(img)
    print(np.mean(img, axis=0))

    avg = np.mean(img, axis=0)
    print(np.min(avg))
    print(np.max(avg))
    print(np.std(avg))

    plt.imshow(img)
    plt.show()
    
    rgb1 = dng.postprocess()  # demosaicing by rawpy
    plt.imshow(rgb1)
    plt.show()
    print(rgb1.shape)

    avg = np.mean(rgb1, axis=(1,2))
    print(avg)
    print(avg.shape)
    print(np.min(avg))
    print(np.max(avg))
    print(np.std(avg))
