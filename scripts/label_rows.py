#!/usr/bin/python

import numpy as np

color_map = [
   "VIOLET",        # 0 
   "ROYAL BLUE",
   "BLUE",
   "CYAN",
   "GREEN",
   "LIME",          # 5
   "AMBER",
   "RED ORANGE",
   "RED",
   "DEEP RED",
   "FAR RED",       # 5
   "WHITE"]


def calc_num_rows(ton, toff, size):

    denom = (ton + toff) / 5.0
    denom = denom if denom > 1 else 1

    return (int)(size/denom)

def calc_labels(mask, ton, toff, black_mul, fname='seq1.npy'):
    seq = np.load(fname)

    n = calc_num_rows(ton, toff, seq.shape[1])
    col_seq = np.ravel(seq[0][:n])

    colors = [color_map[x] for x in col_seq]
    colors.insert(0, "BLUE") 
    colors.append("RED") 

    # Find midpoint of black rows
    black_rows = np.where(np.roll(mask,1) != mask)[0]
    mid = [int((black_rows[i] + black_rows[i+1])/2) for i in range(0, black_rows.size, 2)]
    print(black_rows)
    print(mid)
    print(np.diff(mid))
    print(np.diff(black_rows))

    # print(black_rows[np.where(np.diff(black_rows) < 100)])
    seq_len = np.diff(mid)

    # num_rows_per_led
    print(seq_len)

    return 0


if __name__=="__main__":

    seq = np.load('seq1.npy')
    print(seq)
    print(seq.shape)

    print(calc_num_rows(15, 0, seq.shape[1]))

