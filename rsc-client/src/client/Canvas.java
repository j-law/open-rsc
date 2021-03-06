package client;

import client.res.Resources;
import client.res.Sprite;

/**
 * Class responsible for storing and manipulating pixel data of a 2D image.
 * 
 * <p><i>Based on <code>Surface.java</code> from other RSC sources.</i>
 * 
 * @author Dan Bryce
 */
public class Canvas {

    private static final int COLOUR_BLACK = 0;

    private int width;
    
    private int height;
    
    private int[] pixels;
    
    public Canvas(int width, int height) {
        this.width = width;
        this.height = height;
        
        pixels = new int[width * height];
    }

    public void clear() {
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = COLOUR_BLACK;
        }
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public int[] getPixels() {
        return pixels;
    }

    /**
     * Draws the sprite with the given ID at the given position.
     * 
     * @param x
     * @param y
     * @param id
     */
    public void drawSprite(int x, int y, int id) {

        Sprite sprite = Resources.getSprite(id);

        if (sprite.hasDrawOffset()) {
            x += sprite.getDrawOffsetX();
            y += sprite.getDrawOffsetY();
        }

        int targetIndex = x + y * width;
        int sourceIndex = 0;
        int spriteHeight = sprite.getHeight();
        int spriteWidth = sprite.getWidth();
        int screenRowIncrement = width - spriteWidth;
        int spriteRowIncrement = 0;

        /*
         * Bounds checking.
         *
         * If part of the Sprite is offscreen, this ensures that we only draw
         * the visible part of the image. Attempting to draw the full image
         * would result in parts of the image wrapping onto the next row of
         * pixels.
         */

        if (y < 0) {
            spriteHeight += y;
            sourceIndex -= y * spriteWidth;
            targetIndex -= y * width;
            y = 0;
        }

        if (y + spriteHeight >= height) {
            spriteHeight -= ((y + spriteHeight) - height) + 1;
        }

        if (x < 0) {
            spriteWidth += x;
            sourceIndex -= x;
            targetIndex -= x;
            spriteRowIncrement -= x;
            screenRowIncrement -= x;
            x = 0;
        }

        if (x + spriteWidth >= width) {
            int adjustment = ((x + spriteWidth) - width) + 1;
            spriteWidth -= adjustment;
            spriteRowIncrement += adjustment;
            screenRowIncrement += adjustment;
        }

        if (spriteWidth <= 0 || spriteHeight <= 0) {
            return;
        }
        
        setPixels(pixels, sprite.getPixels(),
                sourceIndex, targetIndex,
                spriteWidth, spriteHeight,
                screenRowIncrement, spriteRowIncrement);
    }

    /**
     * Copies a block of pixels from the source to the target.
     *
     * @param target Target pixel data.
     * @param source Source pixel data.
     * @param sourceIndex Starting index for the source array.
     * @param targetIndex Starting index for the target array.
     * @param sourceWidth Width of the source image.
     * @param sourceHeight Height of the source image.
     * @param targetRowIncrement
     *      Value to add to the target index after each row is copied.
     * @param sourceRowIncrement
     *      Value to add to the source index after each row is copied.
     */
    private static void setPixels(
            int target[], int source[],
            int sourceIndex, int targetIndex,
            int sourceWidth, int sourceHeight,
            int targetRowIncrement, int sourceRowIncrement) {

        /*
         * The original source code copied multiple pixels at a time inside the
         * loop body, presumably intended as some kind of optimisation. Here I
         * have favoured simplicity over efficiency.
         */
        for (int y = 0; y < sourceHeight; y++) {
            for (int x = 0; x < sourceWidth; x++) {

                int colour = source[sourceIndex];
                if (colour != 0) {
                    target[targetIndex] = colour;
                }

                sourceIndex++;
                targetIndex++;
            }

            targetIndex += targetRowIncrement;
            sourceIndex += sourceRowIncrement;
        }
    }

    public void spriteClip(int x, int y, int width, int height, int id) {

        Sprite sprite = Resources.getSprite(id);
        int j1 = sprite.getWidth();
        int k1 = sprite.getHeight();
        int l1 = 0;
        int i2 = 0;
        int j2 = (j1 << 16) / width;
        int k2 = (k1 << 16) / height;
        if (sprite.hasDrawOffset()) {
            int l2 = sprite.getTextureWidth();
            int j3 = sprite.getTextureHeight();
            j2 = (l2 << 16) / width;
            k2 = (j3 << 16) / height;
            x += ((sprite.getDrawOffsetX() * width + l2) - 1) / l2;
            y += ((sprite.getDrawOffsetY() * height + j3) - 1) / j3;
            if ((sprite.getDrawOffsetX() * width) % l2 != 0) {
                l1 = (l2 - (sprite.getDrawOffsetX() * width) % l2 << 16) / width;
            }
            if ((sprite.getDrawOffsetY() * height) % j3 != 0) {
                i2 = (j3 - (sprite.getDrawOffsetY() * height) % j3 << 16) / height;
            }
            width = (width * (sprite.getWidth() - (l1 >> 16))) / l2;
            height = (height * (sprite.getHeight() - (i2 >> 16))) / j3;
        }
        int i3 = x + y * this.width;
        int k3 = this.width - width;
        if (y < 0) {
            int l3 = 0 - y;
            height -= l3;
            y = 0;
            i3 += l3 * this.width;
            i2 += k2 * l3;
        }
        if (y + height >= this.height) {
            height -= ((y + height) - this.height) + 1;
        }
        if (x < 0) {
            int i4 = 0 - x;
            width -= i4;
            x = 0;
            i3 += i4;
            l1 += j2 * i4;
            k3 += i4;
        }
        if (x + width >= this.width) {
            int j4 = ((x + width) - this.width) + 1;
            width -= j4;
            k3 += j4;
        }
        byte byte0 = 1;
        plotSale(sprite.getPixels(), 0, l1, i2, i3, k3, width, height, j2, k2, j1, byte0);
    }

    private void plotSale(int texturePixels[], int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2,
            int j2, int k2) {
        
        int l2 = j;
        for (int i3 = -k1; i3 < 0; i3 += k2) {
            int j3 = (k >> 16) * j2;
            for (int k3 = -j1; k3 < 0; k3++) {
                i = texturePixels[(j >> 16) + j3];
                if (i != 0) {
                    pixels[l++] = i;
                } else {
                    l++;
                }
                j += l1;
            }

            k += i2;
            j = l2;
            l += i1;
        }
    }

    /**
     * Draws a textured scanline.
     * 
     * Used for walls and roofs.
     * 
     * BLACK MAGIC - DO NOT TOUCH.
     * 
     * @param texturePixels
     * @param i
     * @param j
     * @param paramA
     * @param paramB
     * @param paramC
     * @param paramAModifier
     * @param paramBModifier
     * @param paramCModifier
     * @param scanlineSize
     * @param pxOffset
     * @param paramD
     * @param paramDModifier
     */
    public void renderScanline_LargeTexture(
            int texturePixels[],
            int i,
            int j,
            int paramA,
            int paramB,
            int paramC,
            int paramAModifier,
            int paramBModifier,
            int paramCModifier,
            int scanlineSize,
            int pxOffset,
            int paramD,
            int paramDModifier) {
        
        if (scanlineSize <= 0) {
            return;
        }
        
        int i3 = 0;
        int j3 = 0;
        int colourShift = 0;
        
        if (paramC != 0) {
            i = paramA / paramC << 7;
            j = paramB / paramC << 7;
        }

        // Bounds checking
        if (i < 0) {
            i = 0;
        } else if (i > 16256) {
            i = 16256;
        }
        
        paramA += paramAModifier;
        paramB += paramBModifier;
        paramC += paramCModifier;
        
        if (paramC != 0) {
            i3 = paramA / paramC << 7;
            j3 = paramB / paramC << 7;
        }

        // Bounds checking
        if (i3 < 0) {
            i3 = 0;
        } else if (i3 > 16256) {
            i3 = 16256;
        }
        
        int k3 = i3 - i >> 4;
        int l3 = j3 - j >> 4;

        // Draw 16 pixels with each loop iteration
        for (int j4 = 0; j4 < scanlineSize >> 4; j4++) {
            
            /*
             * These next sections could be rolled up into 2 nested for-loops.
             * Presumably this wasn't done for performance reasons.
             */
            
            i += paramD & 0x600000;
            colourShift = paramD >> 23;
            paramD += paramDModifier;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;

            i += k3;
            j += l3;
            i = (i & 0x3fff) + (paramD & 0x600000);
            colourShift = paramD >> 23;
            paramD += paramDModifier;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;

            i += k3;
            j += l3;
            i = (i & 0x3fff) + (paramD & 0x600000);
            colourShift = paramD >> 23;
            paramD += paramDModifier;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;

            i += k3;
            j += l3;
            i = (i & 0x3fff) + (paramD & 0x600000);
            colourShift = paramD >> 23;
            paramD += paramDModifier;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;

            i = i3;
            j = j3;
            paramA += paramAModifier;
            paramB += paramBModifier;
            paramC += paramCModifier;
            
            if (paramC != 0) {
                i3 = paramA / paramC << 7;
                j3 = paramB / paramC << 7;
            }

            // Bounds checking
            if (i3 < 0) {
                i3 = 0;
            } else if (i3 > 16256) {
                i3 = 16256;
            }
            
            k3 = i3 - i >> 4;
            l3 = j3 - j >> 4;
        }

        // Render the last part of the scanline
        for (int k4 = 0; k4 < (scanlineSize & 0xf); k4++) {
            if ((k4 & 3) == 0) {
                i = (i & 0x3fff) + (paramD & 0x600000);
                colourShift = paramD >> 23;
                paramD += paramDModifier;
            }
            pixels[pxOffset++] = texturePixels[(j & 0x3f80) + (i >> 7)] >>> colourShift;
            i += k3;
            j += l3;
        }
    }

    public void renderScanline_LargeTranslucentTexture(
            int texturePixels[],
            int i,
            int j,
            int k,
            int l,
            int i1,
            int j1,
            int k1,
            int l1,
            int scanlineSize,
            int pxOffset,
            int k2,
            int l2) {

        if (scanlineSize <= 0) {
            return;
        }
        
        int i3 = 0;
        int j3 = 0;
        int i4 = 0;
        
        if (i1 != 0) {
            i = k / i1 << 7;
            j = l / i1 << 7;
        }
        
        if (i < 0) {
            i = 0;
        } else if (i > 16256) {
            i = 16256;
        }
        
        k += j1;
        l += k1;
        i1 += l1;
        
        if (i1 != 0) {
            i3 = k / i1 << 7;
            j3 = l / i1 << 7;
        }
        
        if (i3 < 0) {
            i3 = 0;
        } else if (i3 > 16256) {
            i3 = 16256;
        }
        
        int k3 = i3 - i >> 4;
        int l3 = j3 - j >> 4;
            
        for (int j4 = scanlineSize >> 4; j4 > 0; j4--) {
            
            i += k2 & 0x600000;
            i4 = k2 >> 23;
            k2 += l2;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            
            i = (i & 0x3fff) + (k2 & 0x600000);
            i4 = k2 >> 23;
            k2 += l2;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            
            i = (i & 0x3fff) + (k2 & 0x600000);
            i4 = k2 >> 23;
            k2 += l2;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            
            i = (i & 0x3fff) + (k2 & 0x600000);
            i4 = k2 >> 23;
            k2 += l2;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i = i3;
            j = j3;
            
            k += j1;
            l += k1;
            i1 += l1;
            
            if (i1 != 0) {
                i3 = k / i1 << 7;
                j3 = l / i1 << 7;
            }
            
            if (i3 < 0) {
                i3 = 0;
            } else if (i3 > 16256) {
                i3 = 16256;
            }
            
            k3 = i3 - i >> 4;
            l3 = j3 - j >> 4;
        }

        // Render the last part of the scanline
        for (int k4 = 0; k4 < (scanlineSize & 0xf); k4++) {
            if ((k4 & 3) == 0) {
                i = (i & 0x3fff) + (k2 & 0x600000);
                i4 = k2 >> 23;
                k2 += l2;
            }
            pixels[pxOffset++] = (texturePixels[(j & 0x3f80) + (i >> 7)] >>> i4) + (pixels[pxOffset] >> 1 & 0x7f7f7f);
            i += k3;
            j += l3;
        }
    }

    public void renderScanline_LargeTextureWithTransparency(
            int i,
            int j,
            int k,
            int texturePixels[],
            int l,
            int i1,
            int j1,
            int k1,
            int l1,
            int i2,
            int scanlineSize,
            int pxOffset,
            int l2,
            int i3) {

        if (scanlineSize <= 0) {
            return;
        }
        
        int j3 = 0;
        int k3 = 0;
        i3 <<= 2;
        
        if (j1 != 0) {
            j3 = l / j1 << 7;
            k3 = i1 / j1 << 7;
        }
        
        if (j3 < 0) {
            j3 = 0;
        } else if (j3 > 16256) {
            j3 = 16256;
        }
        
        for (int j4 = scanlineSize; j4 > 0; j4 -= 16) {
            
            l += k1;
            i1 += l1;
            j1 += i2;
            j = j3;
            k = k3;
            
            if (j1 != 0) {
                j3 = l / j1 << 7;
                k3 = i1 / j1 << 7;
            }
            
            if (j3 < 0) {
                j3 = 0;
            } else if (j3 > 16256) {
                j3 = 16256;
            }
            
            int l3 = j3 - j >> 4;
            int i4 = k3 - k >> 4;
            int k4 = l2 >> 23;
            
            j += l2 & 0x600000;
            l2 += i3;
            
            if (j4 < 16) {
                
                // Render fewer than 16 pixels
                
                for (int l4 = 0; l4 < j4; l4++) {
                    
                    if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                        pixels[pxOffset] = i;
                    }
                    
                    pxOffset++;
                    j += l3;
                    k += i4;
                    
                    if ((l4 & 3) == 3) {
                        j = (j & 0x3fff) + (l2 & 0x600000);
                        k4 = l2 >> 23;
                        l2 += i3;
                    }
                }

            } else {

                // Render 16 pixels
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                j = (j & 0x3fff) + (l2 & 0x600000);
                k4 = l2 >> 23;
                l2 += i3;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                j = (j & 0x3fff) + (l2 & 0x600000);
                k4 = l2 >> 23;
                l2 += i3;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                j = (j & 0x3fff) + (l2 & 0x600000);
                k4 = l2 >> 23;
                l2 += i3;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
                j += l3;
                k += i4;
                
                if ((i = texturePixels[(k & 0x3f80) + (j >> 7)] >>> k4) != 0) {
                    pixels[pxOffset] = i;
                }
                pxOffset++;
            }
        }
    }

    /*
     * Used for wooden floors!
     */
    public void renderScanline_SmallTexture(int texturePixels[], int i, int j, int k, int l, int i1, int j1, int k1,
            int l1, int i2, int j2, int k2, int l2) {

        if (i2 <= 0) {
            return;
        }
        
        int i3 = 0;
        int j3 = 0;
        l2 <<= 2;
        if (i1 != 0) {
            i3 = k / i1 << 6;
            j3 = l / i1 << 6;
        }
        if (i3 < 0) {
            i3 = 0;
        } else if (i3 > 4032) {
            i3 = 4032;
        }
        for (int i4 = i2; i4 > 0; i4 -= 16) {
            k += j1;
            l += k1;
            i1 += l1;
            i = i3;
            j = j3;
            if (i1 != 0) {
                i3 = k / i1 << 6;
                j3 = l / i1 << 6;
            }
            if (i3 < 0) {
                i3 = 0;
            } else if (i3 > 4032) {
                i3 = 4032;
            }
            int k3 = i3 - i >> 4;
            int l3 = j3 - j >> 4;
            int j4 = k2 >> 20;
            i += k2 & 0xc0000;
            k2 += l2;
            if (i4 < 16) {
                for (int k4 = 0; k4 < i4; k4++) {
                    pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                    i += k3;
                    j += l3;
                    if ((k4 & 3) == 3) {
                        i = (i & 0xfff) + (k2 & 0xc0000);
                        j4 = k2 >> 20;
                        k2 += l2;
                    }
                }

            } else {
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                i = (i & 0xfff) + (k2 & 0xc0000);
                j4 = k2 >> 20;
                k2 += l2;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                i = (i & 0xfff) + (k2 & 0xc0000);
                j4 = k2 >> 20;
                k2 += l2;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                i = (i & 0xfff) + (k2 & 0xc0000);
                j4 = k2 >> 20;
                k2 += l2;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
                i += k3;
                j += l3;
                pixels[j2++] = texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4;
            }
        }

    }

    public void renderScanline_SmallTranslucentTexture(int texturePixels[], int i, int j, int k, int l, int i1, int j1,
            int k1, int l1, int i2, int j2, int k2, int l2) {

        if (i2 <= 0) {
            return;
        }
        int i3 = 0;
        int j3 = 0;
        l2 <<= 2;
        if (i1 != 0) {
            i3 = k / i1 << 6;
            j3 = l / i1 << 6;
        }
        if (i3 < 0) {
            i3 = 0;
        } else if (i3 > 4032) {
            i3 = 4032;
        }
        for (int i4 = i2; i4 > 0; i4 -= 16) {
            k += j1;
            l += k1;
            i1 += l1;
            i = i3;
            j = j3;
            if (i1 != 0) {
                i3 = k / i1 << 6;
                j3 = l / i1 << 6;
            }
            if (i3 < 0) {
                i3 = 0;
            } else if (i3 > 4032) {
                i3 = 4032;
            }
            int k3 = i3 - i >> 4;
            int l3 = j3 - j >> 4;
            int j4 = k2 >> 20;
            i += k2 & 0xc0000;
            k2 += l2;
            if (i4 < 16) {
                for (int k4 = 0; k4 < i4; k4++) {
                    pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                    i += k3;
                    j += l3;
                    if ((k4 & 3) == 3) {
                        i = (i & 0xfff) + (k2 & 0xc0000);
                        j4 = k2 >> 20;
                        k2 += l2;
                    }
                }

            } else {
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                i = (i & 0xfff) + (k2 & 0xc0000);
                j4 = k2 >> 20;
                k2 += l2;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                i = (i & 0xfff) + (k2 & 0xc0000);
                j4 = k2 >> 20;
                k2 += l2;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                i = (i & 0xfff) + (k2 & 0xc0000);
                j4 = k2 >> 20;
                k2 += l2;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
                i += k3;
                j += l3;
                pixels[j2++] = (texturePixels[(j & 0xfc0) + (i >> 6)] >>> j4) + (pixels[j2] >> 1 & 0x7f7f7f);
            }
        }

    }

    public void renderScanline_SmallTextureWithTransparency(int i, int j, int k, int texturePixels[], int l, int i1, int j1,
            int k1, int l1, int i2, int j2, int k2, int l2, int i3) {
        
        if (j2 <= 0) {
            return;
        }
        int j3 = 0;
        int k3 = 0;
        i3 <<= 2;
        if (j1 != 0) {
            j3 = l / j1 << 6;
            k3 = i1 / j1 << 6;
        }
        if (j3 < 0) {
            j3 = 0;
        } else if (j3 > 4032) {
            j3 = 4032;
        }
        for (int j4 = j2; j4 > 0; j4 -= 16) {
            l += k1;
            i1 += l1;
            j1 += i2;
            j = j3;
            k = k3;
            if (j1 != 0) {
                j3 = l / j1 << 6;
                k3 = i1 / j1 << 6;
            }
            if (j3 < 0) {
                j3 = 0;
            } else if (j3 > 4032) {
                j3 = 4032;
            }
            int l3 = j3 - j >> 4;
            int i4 = k3 - k >> 4;
            int k4 = l2 >> 20;
            j += l2 & 0xc0000;
            l2 += i3;
            if (j4 < 16) {
                for (int l4 = 0; l4 < j4; l4++) {
                    if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                        pixels[k2] = i;
                    }
                    k2++;
                    j += l3;
                    k += i4;
                    if ((l4 & 3) == 3) {
                        j = (j & 0xfff) + (l2 & 0xc0000);
                        k4 = l2 >> 20;
                        l2 += i3;
                    }
                }

            } else {
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                j = (j & 0xfff) + (l2 & 0xc0000);
                k4 = l2 >> 20;
                l2 += i3;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                j = (j & 0xfff) + (l2 & 0xc0000);
                k4 = l2 >> 20;
                l2 += i3;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                j = (j & 0xfff) + (l2 & 0xc0000);
                k4 = l2 >> 20;
                l2 += i3;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
                j += l3;
                k += i4;
                if ((i = texturePixels[(k & 0xfc0) + (j >> 6)] >>> k4) != 0) {
                    pixels[k2] = i;
                }
                k2++;
            }
        }

    }

    public void renderScanline_TranslucentGradient(int i, int j, int k, int ai1[], int l, int i1) {
        
        if (i >= 0) {
            return;
        }
        i1 <<= 2;
        k = ai1[l >> 8 & 0xff];
        l += i1;
        int j1 = i / 16;
        for (int k1 = j1; k1 < 0; k1++) {
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            k = ai1[l >> 8 & 0xff];
            l += i1;
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            k = ai1[l >> 8 & 0xff];
            l += i1;
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            k = ai1[l >> 8 & 0xff];
            l += i1;
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            k = ai1[l >> 8 & 0xff];
            l += i1;
        }

        j1 = -(i % 16);
        for (int l1 = 0; l1 < j1; l1++) {
            pixels[j++] = k + (pixels[j] >> 1 & 0x7f7f7f);
            if ((l1 & 3) == 3) {
                k = ai1[l >> 8 & 0xff];
                l += i1;
                l += i1;
            }
        }

    }

    /*
     * Used for grass!
     */
    public void renderScanline_Gradient(int i, int j, int k, int currentGradientRamps[], int l, int i1) {
        
        if (i >= 0) {
            return;
        }
        i1 <<= 2;
        k = currentGradientRamps[l >> 8 & 0xff];
        l += i1;
        int j1 = i / 16;
        for (int k1 = j1; k1 < 0; k1++) {
            pixels[j++] = k;
            pixels[j++] = k;
            pixels[j++] = k;
            pixels[j++] = k;
            k = currentGradientRamps[l >> 8 & 0xff];
            l += i1;
            pixels[j++] = k;
            pixels[j++] = k;
            pixels[j++] = k;
            pixels[j++] = k;
            k = currentGradientRamps[l >> 8 & 0xff];
            l += i1;
            pixels[j++] = k;
            pixels[j++] = k;
            pixels[j++] = k;
            pixels[j++] = k;
            k = currentGradientRamps[l >> 8 & 0xff];
            l += i1;
            pixels[j++] = k;
            pixels[j++] = k;
            pixels[j++] = k;
            pixels[j++] = k;
            k = currentGradientRamps[l >> 8 & 0xff];
            l += i1;
        }

        j1 = -(i % 16);
        for (int l1 = 0; l1 < j1; l1++) {
            pixels[j++] = k;
            if ((l1 & 3) == 3) {
                k = currentGradientRamps[l >> 8 & 0xff];
                l += i1;
            }
        }

    }

    public void drawLineX(int x1, int y, int x2, int colour) {
        
        if (y < 0 || y >= height) {
            // Line is outside the image bounds
            return;
        }
        
        if (x1 < 0) {
            // Ensure we don't start outside the image bounds
            x2 -= 0 - x1;
            x1 = 0;
        }
        
        if (x1 + x2 > width) {
            // Ensure we don't finish outside the image bounds
            x2 = width - x1;
        }
        
        int startIndex = x1 + y * width;
        
        for (int i = 0; i < x2; i++) {
            pixels[startIndex + i] = colour;
        }
    }

}
