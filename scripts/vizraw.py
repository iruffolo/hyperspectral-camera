import cv2
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.pyplot import figure
import rawpy
import os
import skimage
from skimage import exposure

def readDNG(filename):
    dng_path = f"{os.getcwd()}/raw_data/{filename}"
    dng = rawpy.imread(dng_path)
    raw_image = dng.raw_image.astype(int)
    return dng, raw_image
    
def printImage(image, dpi=200):
    figure(dpi=dpi)
    plt.imshow(image, cmap='gray')
    plt.show()
    
filename = "white.dng"
dng, raw = readDNG(filename)
raw = raw
# printImage(raw)


# Linearize image
def linearize(raw, dng):
    black = dng.black_level_per_channel[0]
    saturation = dng.white_level
    raw -= black
    uint10_max = 2**10 - 1
    raw = raw * int(uint10_max/(saturation - black))
    raw = np.clip(raw,0,uint10_max)
    return raw

raw = linearize(raw, dng)
# printImage(raw)

# Separate colors
def seperateColors(raw, dng):
    bayer = dng.raw_colors
    assert dng.color_desc == b'RGBG'
    
    newshape = (raw.shape[0]//2, raw.shape[1]//2)
    # Red
    red_idx = np.where(bayer == 0)
    red = raw[red_idx]
    red = np.reshape(red, newshape)

    # Green
    green_idx = np.where((bayer == 1) | (bayer == 3))
    green = raw[green_idx]
    green = np.reshape(green, (raw.shape[0], raw.shape[1]//2))
    
    # Blue
    blue_idx = np.where(bayer == 2)
    blue = raw[blue_idx]
    blue = np.reshape(blue, newshape)
    
    return red, green, blue

red, green, blue = seperateColors(raw, dng)

avg = np.mean(raw, -1)
