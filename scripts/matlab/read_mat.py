#!/usr/bin/python3

import scipy.io
import matplotlib.pyplot as plt
import numpy as np


if __name__ == "__main__":

    data_dir = "/home/ian/dev/KonicaMinolta-CS2000-MATLAB/data/colorchecker"

    mat = scipy.io.loadmat(f"{data_dir}/measure6x3_spectral")

    sd = mat["spectralData"]
    print(sd)

    print(sd.shape)
    lmin = 380
    lmax = 780
    step = 1

    x = list(range(lmin, lmax+1, step))

    plt.plot(x, sd[0])
    plt.gca().set_ylim([np.min(sd), np.max(sd)])
    plt.gca().set_xlim([lmin, lmax])
    plt.show()
