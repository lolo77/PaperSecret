package com.topsecret.paper.sandbox;

public class EdgeHVFilter {
    public static int[][] filter(int[][] image) {
        int[][] result = new int[image.length][image[0].length];

        // Prewitt
//        int[][] kernelX = {{-1, 0, 1}, {-1, 0, 1}, {-1, 0, 1}};
//        int[][] kernelY = {{-1, -1, -1}, {0, 0, 0}, {1, 1, 1}};

        // Sobel
        int[][] kernelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
        int[][] kernelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};

        for (int y = 0; y < image.length; y++) {
            for (int x = 0; x < image[y].length; x++) {
                int gx = 0, gy = 0;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        int x_coord = x + j;
                        int y_coord = y + i;
                        if (x_coord >= 0 && x_coord < image[y].length && y_coord >= 0 && y_coord < image.length) {
                            gx += image[y_coord][x_coord] * kernelX[i + 1][j + 1];
                            gy += image[y_coord][x_coord] * kernelY[i + 1][j + 1];
                        }
                    }
                }
                int g = (int) Math.sqrt(gx * gx + gy * gy);
                result[y][x] = g;
            }
        }
        return result;
    }
}
