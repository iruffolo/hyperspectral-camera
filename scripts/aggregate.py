#!/usr/bin/python3

import numpy as np


def aggregate_results(patches, num_row, num_col, overlap_row, overlap_col):

    x = patches.shape[1]
    y = patches.shape[2]
    channels = patches.shape[3]

    row_dim = x*num_row - (num_row - 1)*overlap_row
    col_dim = y*num_col - (num_col - 1)*overlap_col

    agg_rows = np.zeros([num_col, x, col_dim, channels])
    agg_cols = np.zeros([num_row, row_dim, col_dim, channels])
    # print(agg_rows.shape)
    # print(agg_cols.shape)

    # Stack columns with offset and compute
    for i in range(num_row):
        print(f"rows {i}")
        for j in range(num_col):
            idx_start = j*y - j*overlap_col
            idx_end = (j+1)*y - j*overlap_col
            print(f"Row index {idx_start, idx_end}")

            agg_rows[j, :, idx_start:idx_end] += patches[i*num_col + j]

        idx_start = i*y - i*overlap_row
        idx_end = (i+1)*y - i*overlap_row
        print(f"Col index {idx_start, idx_end}")

        agg_cols[i, idx_start:idx_end, :] = np.mean(agg_rows, axis=0)

    result = np.mean(agg_cols, axis=0)

    return result


if __name__ == "__main__":

    x = np.random.uniform(0, 1, [48, 500, 500, 31])

    res = aggregate_results(x, 6, 8, 200, 200)

    print(res.shape)
