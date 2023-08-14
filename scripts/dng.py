import cv2
import numpy as np
import matplotlib.pyplot as plt
from process_raw import DngFile

filenames = ["IMG_RS0_2023_08_13_21_11_36_245.dng"]

for filename in filenames:
    # Download raw.dng for test:
    dng_path = f"./{filename}.dng"
    dng_path = filename

    dng = DngFile.read(dng_path)

    raw = dng.raw  # np.uint16
    raw_8bit = np.uint8(raw >> (dng.bit-8))

    plt.imshow(raw)
    plt.show()

    plt.show

    cv2.imwrite(f"{filename}_raw_8bit.png", raw_8bit)

    rgb1 = dng.postprocess()  # demosaicing by rawpy
    cv2.imwrite(f"{filename}_rgb1.jpg", rgb1[:, :, ::-1])
    rgb2 = dng.demosaicing(poww=0.3)  # demosaicing with gamma correction 0.3
    cv2.imwrite(f"{filename}_rgb2.jpg", rgb2[:, :, ::-1])
    DngFile.save(dng_path + "-save.dng",
                 dng.raw, bit=dng.bit, pattern=dng.pattern)
