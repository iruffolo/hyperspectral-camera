#!/usr/bin/python3

import scipy.io
import matplotlib.pyplot as plt
import numpy as np
import glob


def plot_spectral(data, lmin=380, lmax=780, step=1):
    # x = np.array(range(lmin, lmax+step, step))

    plt.plot(data[0], data[1])
    plt.gca().set_ylim([np.min(data[1]), np.max(data[1])])
    plt.gca().set_xlim([lmin, lmax])
    plt.show()


def bin_spectral(data, lmin=380, lmax=780):

    x = list(range(lmin, lmax+1, 1))

    bins = np.array(range(lmin, lmax, 10))
    digitized = np.digitize(x, bins)

    bin_sums = np.array([data[digitized == i].sum()
                        for i in range(1, len(bins))])

    bin_midpoints = (bins[1:] + bins[:-1])/2

    binned_data = np.stack([bin_midpoints[1:-7], bin_sums[1:-7]], axis=0)

    return binned_data


def save_color_checker():
    # Color Checker
    cc = list()

    for i in range(1, 7):
        for j in range(1, 5):
            f = f"colorchecker/measure{i}x{j}_spectral.mat"
            cc.append(load_mat(f)[1])

            # plot_spectral(binned_sd, lmin=385, lmax=695, step=10)

    cc = np.array(cc)
    print(f"Color checker size: {cc.shape}")

    np.save("cc", cc)


def save_leds():
    # LEDs
    leds = list()

    for i in range(0, 12):

        f = glob.glob(f"leds/led{i}_*_spectral.mat")[0]
        leds.append(load_mat(f)[1])
        # plot_spectral(binned_sd, lmin=385, lmax=695, step=10)

    leds = np.array(leds)
    print(f"LEDs size: {leds.shape}")

    np.save("leds", leds)


def save_wc():

    wc0 = load_mat("watercan/laser_pointer0_spectral.mat")[1]
    wc1 = load_mat("watercan/laser_pointer1_spectral.mat")[1]

    wc = np.mean(np.stack([wc0, wc1]), axis=0)
    print(f"WC size: {wc.shape}")

    np.save("wc", wc)


def load_mat(file):
    mat = scipy.io.loadmat(file)
    sd = mat["spectralData"][0]

    return bin_spectral(sd)


if __name__ == "__main__":

    save_color_checker()
    save_leds()
    save_wc()

    # plot_spectral(sd)
