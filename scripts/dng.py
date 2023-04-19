import cv2
import numpy as np
import matplotlib.pyplot as plt
from process_raw import DngFile

filenames = ["LRM_20230209_153302",
             "LRM_20230308_191906",
             "LRM_20230308_194536",
             "LRM_20230308_195133"]

for filename in filenames:
    # Download raw.dng for test:
    dng_path = f"./{filename}.dng"

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
