package client.render;

import client.Canvas;
import client.res.Resources;
import client.res.Texture;
import client.scene.Camera;
import client.scene.Model;
import client.scene.Polygon;
import client.scene.Scene;
import client.scene.SpriteEntity;

/**
 * Class responsible for rendering a scene.
 * 
 * <p>This is essentially a 3d software renderer; we could save a lot of
 * complexity by porting to OpenGL instead.
 * 
 * <p>This class should not be aware of the game at all, and the game should
 * not be aware of what goes on inside this class.
 * 
 * <p><i>Based on <code>client.Scene</code> from EasyRSC.</i>
 */
public class SceneRenderer {

    public static final int COLOUR_TRANSPARENT = 12345678;

    private static final int MAX_POLYGONS = 15000;
    
    private static final int VIEW_DISTANCE = 9;

    private Scene scene;
    private Camera camera;
    private int visiblePolygonCount;
    private Polygon visiblePolygons[] = new Polygon[MAX_POLYGONS];
    
    private MousePicker mousePicker;
    
    private int rampCount = 50;
    private int gradientBase[] = new int[rampCount];
    private int gradientRamps[][] = new int[rampCount][256];
    private int currentGradientRamps[];
    private int width;
    private int baseX;
    private int baseY;
    private int viewDistance = 9;
    private int normalMagnitude = 4;
    private Scanline scanlines[];
    private int minY;
    private int maxY;
    private int planeX[] = new int[40];
    private int planeY[] = new int[40];
    private int vertexShade[] = new int[40];
    private int vertexX[] = new int[40];
    private int vertexY[] = new int[40];
    private int vertexZ[] = new int[40];
    private int newStart;
    private int newEnd;

    private int clipX;
    private int clipY;
    
    /**
     * Min view distance.
     */
    private int clipNear = 5;
    
    /**
     * Max view distance for 3d models.
     */
    private int clipFar3d = 2400 + (Camera.DEFAULT_HEIGHT * 2);
    
    /**
     * Max view distance for sprites.
     */
    private int clipFar2d = 2400 + (Camera.DEFAULT_HEIGHT * 2);

    private class Scanline {
        public int startX;
        public int endX;
        public int startS;
        public int endS;
    }

    public SceneRenderer(Scene scene, int width, int height) {
        this.scene = scene;
        this.camera = scene.getCamera();

        for (int l = 0; l < visiblePolygons.length; l++) {
            visiblePolygons[l] = new Polygon();
        }
        
        setBounds(
                width / 2, height / 2,
                width / 2, height / 2,
                width,
                VIEW_DISTANCE);
    }

    public void render(Canvas canvas) {
        
        int clipXModified = clipX * clipFar3d >> viewDistance;
        int clipYModified = clipY * clipFar3d >> viewDistance;
        camera.prepareForRendering(clipX, clipY, clipFar3d, clipXModified, clipYModified);
        scene.getModels()[scene.getNumModels()] = scene.getSpriteFaces();
        scene.getSpriteFaces().transformState = 2;
        
        for (int i = 0; i < scene.getNumModels(); i++) {
            scene.getModels()[i].project(camera, viewDistance, clipNear);
        }
        
        scene.getModels()[scene.getNumModels()]
                .project(camera, viewDistance, clipNear);
        visiblePolygonCount = 0;
        
        // Draw each model in the scene
        for (int i = 0; i < scene.getNumModels(); i++) {
            
            Model gameModel = scene.getModels()[i];
            
            if (!gameModel.visible) {
                // Model is not visible
                continue;
            }
            
            // Iterate over every face in the model
            for (int face = 0; face < gameModel.numFaces; face++) {
                
                int numVertices = gameModel.faceNumVertices[face];
                int vertices[] = gameModel.faceVertices[face];
                
                // Determine if any vertices are visible
                boolean visible = false;
                for (int vertex = 0; vertex < numVertices; vertex++) {
                    int i1 = gameModel.projectVertexZ[vertices[vertex]];
                    if (i1 <= clipNear || i1 >= clipFar3d) {
                        continue;
                    }
                    visible = true;
                    break;
                }

                if (!visible) {
                    // No visible vertices
                    continue;
                }
                
                // Verify visibility in x-axis?
                int viewXCount = 0;
                for (int vertex = 0; vertex < numVertices; vertex++) {
                    int j1 = gameModel.vertexViewX[vertices[vertex]];
                    if (j1 > -clipX) {
                        viewXCount |= 1;
                    }
                    if (j1 < clipX) {
                        viewXCount |= 2;
                    }
                    if (viewXCount == 3) {
                        break;
                    }
                }
                if (viewXCount != 3) {
                    continue;
                }

                // Verify visibility in y-axis?
                int viewYCount = 0;
                for (int vertex = 0; vertex < numVertices; vertex++) {
                    int k1 = gameModel.vertexViewY[vertices[vertex]];
                    if (k1 > -clipY) {
                        viewYCount |= 1;
                    }
                    if (k1 < clipY) {
                        viewYCount |= 2;
                    }
                    if (viewYCount == 3) {
                        break;
                    }
                }
                if (viewYCount != 3) {
                    continue;
                }
                
                Polygon polygon1 = visiblePolygons[visiblePolygonCount];
                polygon1.gameModel = gameModel;
                polygon1.face = face;
                initialisePolygon3d(visiblePolygonCount);
                int faceFill;
                
                if (polygon1.visibility < 0) {
                    faceFill = gameModel.faceFillFront[face];
                } else {
                    faceFill = gameModel.faceFillBack[face];
                }
                
                if (faceFill == COLOUR_TRANSPARENT) {
                    // Face is transparent
                    continue;
                }
                
                int j2 = 0;
                for (int vertex = 0; vertex < numVertices; vertex++) {
                    j2 += gameModel.projectVertexZ[vertices[vertex]];
                }

                polygon1.depth = j2 / numVertices + gameModel.anInt245;
                polygon1.faceFill = faceFill;
                visiblePolygonCount++;
            }
        }

        // Render 2d models (sprites)
        Model spriteFaces = scene.getSpriteFaces();
        if (spriteFaces.visible) {
            for (int face = 0; face < spriteFaces.numFaces; face++) {
                int faceVertices[] = spriteFaces.faceVertices[face];
                int vertex0 = faceVertices[0];
                int vx = spriteFaces.vertexViewX[vertex0];
                int vy = spriteFaces.vertexViewY[vertex0];
                int vz = spriteFaces.projectVertexZ[vertex0];
                if (vz > clipNear && vz < clipFar2d) {
                    SpriteEntity spriteEntity = scene.getSpriteEntities()[face];
                    int vw = (spriteEntity.getWidth() << viewDistance) / vz;
                    int vh = (spriteEntity.getHeight() << viewDistance) / vz;
                    if (vx - vw / 2 <= clipX && vx + vw / 2 >= -clipX && vy - vh <= clipY && vy >= -clipY) {
                        Polygon polygon2 = visiblePolygons[visiblePolygonCount];
                        polygon2.gameModel = spriteFaces;
                        polygon2.face = face;
                        initialisePolygon2d(visiblePolygonCount);
                        polygon2.depth = (vz + spriteFaces.projectVertexZ[faceVertices[1]]) / 2;
                        visiblePolygonCount++;
                    }
                }
            }

        }
        
        if (visiblePolygonCount == 0) {
            // Nothing to render!
            return;
        }
        
        // Sort polygons
        polygonsQSort(visiblePolygons, 0, visiblePolygonCount - 1);
        polygonsIntersectSort(100, visiblePolygons, visiblePolygonCount);
        
        // Render our polygons!
        for (int polygonIndex = 0; polygonIndex < visiblePolygonCount; polygonIndex++) {
            
            Polygon polygon = visiblePolygons[polygonIndex];
            Model polygonModel = polygon.gameModel;
            int polyFace = polygon.face;
            
            // Is polygon a sprite?
            if (polygonModel == scene.getSpriteFaces()) {
                renderSprite(polygonModel, polyFace, canvas);
                continue;
            }
                
            int plane = 0;
            int light = 0;
            int numVertices = polygonModel.faceNumVertices[polyFace];
            int faceVerts[] = polygonModel.faceVertices[polyFace];
            
            // Calculate face lighting
            if (polygonModel.faceIntensity[polyFace] != COLOUR_TRANSPARENT) {
                if (polygon.visibility < 0) {
                    light = polygonModel.lightAmbience - polygonModel.faceIntensity[polyFace];
                } else {
                    light = polygonModel.lightAmbience + polygonModel.faceIntensity[polyFace];
                }
            }
            
            // Render all vertices
            for (int vertexIndex = 0; vertexIndex < numVertices; vertexIndex++) {
                
                int vertexIndexInModel = faceVerts[vertexIndex];
                vertexX[vertexIndex] = polygonModel.projectVertexX[vertexIndexInModel];
                vertexY[vertexIndex] = polygonModel.projectVertexY[vertexIndexInModel];
                vertexZ[vertexIndex] = polygonModel.projectVertexZ[vertexIndexInModel];
                
                // Calculate vertex lighting for transparent faces
                if (polygonModel.faceIntensity[polyFace] == COLOUR_TRANSPARENT) {
                    if (polygon.visibility < 0) {
                        light = (polygonModel.lightAmbience - polygonModel.vertexIntensity[vertexIndexInModel])
                                + polygonModel.vertexAmbience[vertexIndexInModel];
                    } else {
                        light = polygonModel.lightAmbience + polygonModel.vertexIntensity[vertexIndexInModel]
                                + polygonModel.vertexAmbience[vertexIndexInModel];
                    }
                }
                
                
                if (polygonModel.projectVertexZ[vertexIndexInModel] >= clipNear) {
                    
                    planeX[plane] = polygonModel.vertexViewX[vertexIndexInModel];
                    planeY[plane] = polygonModel.vertexViewY[vertexIndexInModel];
                    vertexShade[plane] = light;
                    
                    if (polygonModel.projectVertexZ[vertexIndexInModel] > scene.fogZDistance) {
                        vertexShade[plane] += (polygonModel.projectVertexZ[vertexIndexInModel] - scene.fogZDistance) / scene.fogZFalloff;
                    }
                    plane++;
                    
                } else {
                    
                    int vertEnd;
                    
                    if (vertexIndex == 0) {
                        vertEnd = faceVerts[numVertices - 1];
                    } else {
                        vertEnd = faceVerts[vertexIndex - 1];
                    }
                    
                    if (polygonModel.projectVertexZ[vertEnd] >= clipNear) {
                        int k7 = polygonModel.projectVertexZ[vertexIndexInModel] - polygonModel.projectVertexZ[vertEnd];
                        int i5 = polygonModel.projectVertexX[vertexIndexInModel]
                                - ((polygonModel.projectVertexX[vertexIndexInModel] - polygonModel.projectVertexX[vertEnd])
                                        * (polygonModel.projectVertexZ[vertexIndexInModel] - clipNear)) / k7;
                        int j6 = polygonModel.projectVertexY[vertexIndexInModel]
                                - ((polygonModel.projectVertexY[vertexIndexInModel] - polygonModel.projectVertexY[vertEnd])
                                        * (polygonModel.projectVertexZ[vertexIndexInModel] - clipNear)) / k7;
                        planeX[plane] = (i5 << viewDistance) / clipNear;
                        planeY[plane] = (j6 << viewDistance) / clipNear;
                        vertexShade[plane] = light;
                        plane++;
                    }
                    
                    if (vertexIndex == numVertices - 1) {
                        vertEnd = faceVerts[0];
                    } else {
                        vertEnd = faceVerts[vertexIndex + 1];
                    }
                    
                    if (polygonModel.projectVertexZ[vertEnd] >= clipNear) {
                        int l7 = polygonModel.projectVertexZ[vertexIndexInModel] - polygonModel.projectVertexZ[vertEnd];
                        int j5 = polygonModel.projectVertexX[vertexIndexInModel]
                                - ((polygonModel.projectVertexX[vertexIndexInModel] - polygonModel.projectVertexX[vertEnd])
                                        * (polygonModel.projectVertexZ[vertexIndexInModel] - clipNear)) / l7;
                        int k6 = polygonModel.projectVertexY[vertexIndexInModel]
                                - ((polygonModel.projectVertexY[vertexIndexInModel] - polygonModel.projectVertexY[vertEnd])
                                        * (polygonModel.projectVertexZ[vertexIndexInModel] - clipNear)) / l7;
                        planeX[plane] = (j5 << viewDistance) / clipNear;
                        planeY[plane] = (k6 << viewDistance) / clipNear;
                        vertexShade[plane] = light;
                        plane++;
                    }
                }
            }

            // Determine vertex shade
            for (int face = 0; face < numVertices; face++) {
                if (vertexShade[face] < 0) {
                    vertexShade[face] = 0;
                } else if (vertexShade[face] > 255) {
                    vertexShade[face] = 255;
                }
                if (polygon.faceFill >= 0) {
                    Texture tex = Resources.textures[polygon.faceFill];
                    if (tex.isLarge()) {
                        vertexShade[face] <<= 9;
                    } else {
                        vertexShade[face] <<= 6;
                    }
                }
            }

            generateScanlines(0, 0, 0, 0, plane, planeX, planeY, vertexShade, polygonModel, polyFace);

            if (maxY > minY) {
                rasterize(canvas, numVertices, vertexX, vertexY, vertexZ, polygon.faceFill, polygonModel);
            }
        }
    }

    private void renderSprite(Model polygonModel, int polyFace,
            Canvas canvas) {
        SpriteEntity spriteEntity = scene.getSpriteEntities()[polyFace];
        int faceverts[] = polygonModel.faceVertices[polyFace];
        int face0 = faceverts[0];
        int vx = polygonModel.vertexViewX[face0];
        int vy = polygonModel.vertexViewY[face0];
        int vz = polygonModel.projectVertexZ[face0];
        int w = (spriteEntity.getWidth() << viewDistance) / vz;
        int h = (spriteEntity.getHeight() << viewDistance) / vz;
        int x = vx - w / 2;
        int y = (baseY + vy) - h;
        canvas.spriteClip(x + baseX, y, w, h, spriteEntity.getId());
    }

    public void setBounds(int baseX, int baseY, int clipX, int clipY, int width, int viewDistance) {
        this.clipX = clipX;
        this.clipY = clipY;
        this.baseX = baseX;
        this.baseY = baseY;
        this.width = width;
        this.viewDistance = viewDistance;
        
        scanlines = new Scanline[clipY + baseY];
        for (int i = 0; i < clipY + baseY; i++) {
            scanlines[i] = new Scanline();
        }
        
        mousePicker = new MousePicker(baseX);
    }

    private static void polygonsQSort(Polygon[] polygons, int low, int high) {
        if (low < high) {
            int min = low - 1;
            int max = high + 1;
            int mid = (low + high) / 2;
            Polygon tmp = polygons[mid];
            polygons[mid] = polygons[low];
            polygons[low] = tmp;
            int j1 = tmp.depth;
            while (min < max) {
                do {
                    max--;
                } while (polygons[max].depth < j1);
                do {
                    min++;
                } while (polygons[min].depth > j1);
                if (min < max) {
                    Polygon polygon = polygons[min];
                    polygons[min] = polygons[max];
                    polygons[max] = polygon;
                }
            }
            polygonsQSort(polygons, low, max);
            polygonsQSort(polygons, max + 1, high);
        }
    }

    private void polygonsIntersectSort(int step, Polygon[] polygons, int count) {
        for (int k = 0; k <= count; k++) {
            polygons[k].skipSomething = false;
            polygons[k].index = k;
            polygons[k].index2 = -1;
        }

        int l = 0;
        do {
            while (polygons[l].skipSomething) {
                l++;
            }
            if (l == count) {
                return;
            }
            Polygon polygon = polygons[l];
            polygon.skipSomething = true;
            int i1 = l;
            int j1 = l + step;
            if (j1 >= count) {
                j1 = count - 1;
            }
            for (int k1 = j1; k1 >= i1 + 1; k1--) {
                Polygon other = polygons[k1];
                if (polygon.minPlaneX < other.maxPlaneX && other.minPlaneX < polygon.maxPlaneX
                        && polygon.minPlaneY < other.maxPlaneY && other.minPlaneY < polygon.maxPlaneY
                        && polygon.index != other.index2 && !arePolygonsSeparate(polygon, other)
                        && heuristicPolygon(other, polygon)) {
                    polygonsOrder(polygons, i1, k1);
                    if (polygons[k1] != other) {
                        k1++;
                    }
                    i1 = newStart;
                    other.index2 = polygon.index;
                }
            }

        } while (true);
    }

    private boolean polygonsOrder(Polygon[] polygons, int start, int end) {
        do {
            Polygon polygon = polygons[start];
            for (int k = start + 1; k <= end; k++) {
                Polygon tmp = polygons[k];
                if (!arePolygonsSeparate(tmp, polygon)) {
                    break;
                }
                polygons[start] = tmp;
                polygons[k] = polygon;
                start = k;
                if (start == end) {
                    newStart = start;
                    newEnd = start - 1;
                    return true;
                }
            }

            Polygon polygon2 = polygons[end];
            for (int l = end - 1; l >= start; l--) {
                Polygon entity_3 = polygons[l];
                if (!arePolygonsSeparate(polygon2, entity_3)) {
                    break;
                }
                polygons[end] = entity_3;
                polygons[l] = polygon2;
                end = l;
                if (start == end) {
                    newStart = end + 1;
                    newEnd = end;
                    return true;
                }
            }

            if (start + 1 >= end) {
                newStart = start;
                newEnd = end;
                return false;
            }
            if (!polygonsOrder(polygons, start + 1, end)) {
                newStart = start;
                return false;
            }
            end = newEnd;
        } while (true);
    }

    private void generateScanlines(
            int i,
            int j,
            int k,
            int l,
            int plane,
            int planeX[],
            int planeY[],
            int vertexShade[],
            Model gameModel,
            int faceId) {
        
        if (plane == 3) {
            
            int k1 = planeY[0] + baseY;
            int k2 = planeY[1] + baseY;
            int k3 = planeY[2] + baseY;
            int k4 = planeX[0];
            int l5 = planeX[1];
            int j7 = planeX[2];
            int l8 = vertexShade[0];
            int j10 = vertexShade[1];
            int j11 = vertexShade[2];
            int j12 = (baseY + clipY) - 1;
            int l12 = 0;
            int j13 = 0;
            int l13 = 0;
            int j14 = 0;
            int l14 = COLOUR_TRANSPARENT;
            int j15 = 0xff439eb2;
            
            if (k3 != k1) {
                
                j13 = (j7 - k4 << 8) / (k3 - k1);
                j14 = (j11 - l8 << 8) / (k3 - k1);
                
                if (k1 < k3) {
                    l12 = k4 << 8;
                    l13 = l8 << 8;
                    l14 = k1;
                    j15 = k3;
                } else {
                    l12 = j7 << 8;
                    l13 = j11 << 8;
                    l14 = k3;
                    j15 = k1;
                }
                
                if (l14 < 0) {
                    l12 -= j13 * l14;
                    l13 -= j14 * l14;
                    l14 = 0;
                }
                
                if (j15 > j12) {
                    j15 = j12;
                }
            }
            
            int l15 = 0;
            int j16 = 0;
            int l16 = 0;
            int j17 = 0;
            int l17 = COLOUR_TRANSPARENT;
            int j18 = 0xff439eb2;
            
            if (k2 != k1) {
                
                j16 = (l5 - k4 << 8) / (k2 - k1);
                j17 = (j10 - l8 << 8) / (k2 - k1);
                
                if (k1 < k2) {
                    l15 = k4 << 8;
                    l16 = l8 << 8;
                    l17 = k1;
                    j18 = k2;
                } else {
                    l15 = l5 << 8;
                    l16 = j10 << 8;
                    l17 = k2;
                    j18 = k1;
                }
                
                if (l17 < 0) {
                    l15 -= j16 * l17;
                    l16 -= j17 * l17;
                    l17 = 0;
                }
                
                if (j18 > j12) {
                    j18 = j12;
                }
            }
            
            int l18 = 0;
            int j19 = 0;
            int l19 = 0;
            int j20 = 0;
            int l20 = COLOUR_TRANSPARENT;
            int j21 = 0xff439eb2;
            
            if (k3 != k2) {
                
                j19 = (j7 - l5 << 8) / (k3 - k2);
                j20 = (j11 - j10 << 8) / (k3 - k2);
                
                if (k2 < k3) {
                    l18 = l5 << 8;
                    l19 = j10 << 8;
                    l20 = k2;
                    j21 = k3;
                } else {
                    l18 = j7 << 8;
                    l19 = j11 << 8;
                    l20 = k3;
                    j21 = k2;
                }
                
                if (l20 < 0) {
                    l18 -= j19 * l20;
                    l19 -= j20 * l20;
                    l20 = 0;
                }
                
                if (j21 > j12) {
                    j21 = j12;
                }
            }
            
            minY = l14;
            if (l17 < minY) {
                minY = l17;
            }
            if (l20 < minY) {
                minY = l20;
            }
            
            maxY = j15;
            if (j18 > maxY) {
                maxY = j18;
            }
            if (j21 > maxY) {
                maxY = j21;
            }
            
            int l21 = 0;
            
            for (k = minY; k < maxY; k++) {
                
                if (k >= l14 && k < j15) {
                    i = j = l12;
                    l = l21 = l13;
                    l12 += j13;
                    l13 += j14;
                } else {
                    i = 0xa0000;
                    j = 0xfff60000;
                }
                
                if (k >= l17 && k < j18) {
                    if (l15 < i) {
                        i = l15;
                        l = l16;
                    }
                    if (l15 > j) {
                        j = l15;
                        l21 = l16;
                    }
                    l15 += j16;
                    l16 += j17;
                }
                
                if (k >= l20 && k < j21) {
                    if (l18 < i) {
                        i = l18;
                        l = l19;
                    }
                    if (l18 > j) {
                        j = l18;
                        l21 = l19;
                    }
                    l18 += j19;
                    l19 += j20;
                }
                
                Scanline cameraVariables_6 = scanlines[k];
                cameraVariables_6.startX = i;
                cameraVariables_6.endX = j;
                cameraVariables_6.startS = l;
                cameraVariables_6.endS = l21;
            }

            if (minY < baseY - clipY) {
                minY = baseY - clipY;
            }
            
        } else if (plane == 4) {
            
            int l1 = planeY[0] + baseY;
            int l2 = planeY[1] + baseY;
            int l3 = planeY[2] + baseY;
            int l4 = planeY[3] + baseY;
            int i6 = planeX[0];
            int k7 = planeX[1];
            int i9 = planeX[2];
            int k10 = planeX[3];
            int k11 = vertexShade[0];
            int k12 = vertexShade[1];
            int i13 = vertexShade[2];
            int k13 = vertexShade[3];
            int i14 = (baseY + clipY) - 1;
            int k14 = 0;
            int i15 = 0;
            int k15 = 0;
            int i16 = 0;
            int k16 = COLOUR_TRANSPARENT;
            int i17 = 0xff439eb2;
            if (l4 != l1) {
                
                i15 = (k10 - i6 << 8) / (l4 - l1);
                i16 = (k13 - k11 << 8) / (l4 - l1);
                
                if (l1 < l4) {
                    k14 = i6 << 8;
                    k15 = k11 << 8;
                    k16 = l1;
                    i17 = l4;
                } else {
                    k14 = k10 << 8;
                    k15 = k13 << 8;
                    k16 = l4;
                    i17 = l1;
                }
                
                if (k16 < 0) {
                    k14 -= i15 * k16;
                    k15 -= i16 * k16;
                    k16 = 0;
                }
                
                if (i17 > i14) {
                    i17 = i14;
                }
            }
            int k17 = 0;
            int i18 = 0;
            int k18 = 0;
            int i19 = 0;
            int k19 = COLOUR_TRANSPARENT;
            int i20 = 0xff439eb2;
            if (l2 != l1) {
                
                i18 = (k7 - i6 << 8) / (l2 - l1);
                i19 = (k12 - k11 << 8) / (l2 - l1);
                
                if (l1 < l2) {
                    k17 = i6 << 8;
                    k18 = k11 << 8;
                    k19 = l1;
                    i20 = l2;
                } else {
                    k17 = k7 << 8;
                    k18 = k12 << 8;
                    k19 = l2;
                    i20 = l1;
                }
                
                if (k19 < 0) {
                    k17 -= i18 * k19;
                    k18 -= i19 * k19;
                    k19 = 0;
                }
                
                if (i20 > i14) {
                    i20 = i14;
                }
            }
            int k20 = 0;
            int i21 = 0;
            int k21 = 0;
            int i22 = 0;
            int j22 = COLOUR_TRANSPARENT;
            int k22 = 0xff439eb2;
            
            if (l3 != l2) {
                
                i21 = (i9 - k7 << 8) / (l3 - l2);
                i22 = (i13 - k12 << 8) / (l3 - l2);
                
                if (l2 < l3) {
                    k20 = k7 << 8;
                    k21 = k12 << 8;
                    j22 = l2;
                    k22 = l3;
                } else {
                    k20 = i9 << 8;
                    k21 = i13 << 8;
                    j22 = l3;
                    k22 = l2;
                }
                
                if (j22 < 0) {
                    k20 -= i21 * j22;
                    k21 -= i22 * j22;
                    j22 = 0;
                }
                
                if (k22 > i14) {
                    k22 = i14;
                }
            }
            int l22 = 0;
            int i23 = 0;
            int j23 = 0;
            int k23 = 0;
            int l23 = COLOUR_TRANSPARENT;
            int i24 = 0xff439eb2;
            
            if (l4 != l3) {
                
                i23 = (k10 - i9 << 8) / (l4 - l3);
                k23 = (k13 - i13 << 8) / (l4 - l3);
                
                if (l3 < l4) {
                    l22 = i9 << 8;
                    j23 = i13 << 8;
                    l23 = l3;
                    i24 = l4;
                } else {
                    l22 = k10 << 8;
                    j23 = k13 << 8;
                    l23 = l4;
                    i24 = l3;
                }
                
                if (l23 < 0) {
                    l22 -= i23 * l23;
                    j23 -= k23 * l23;
                    l23 = 0;
                }
                
                if (i24 > i14) {
                    i24 = i14;
                }
            }
            
            minY = k16;
            if (k19 < minY) {
                minY = k19;
            }
            if (j22 < minY) {
                minY = j22;
            }
            if (l23 < minY) {
                minY = l23;
            }
            
            maxY = i17;
            if (i20 > maxY) {
                maxY = i20;
            }
            if (k22 > maxY) {
                maxY = k22;
            }
            if (i24 > maxY) {
                maxY = i24;
            }
            
            int j24 = 0;
            
            for (k = minY; k < maxY; k++) {
                
                if (k >= k16 && k < i17) {
                    i = j = k14;
                    l = j24 = k15;
                    k14 += i15;
                    k15 += i16;
                } else {
                    i = 0xa0000;
                    j = 0xfff60000;
                }
                
                if (k >= k19 && k < i20) {
                    if (k17 < i) {
                        i = k17;
                        l = k18;
                    }
                    if (k17 > j) {
                        j = k17;
                        j24 = k18;
                    }
                    k17 += i18;
                    k18 += i19;
                }
                
                if (k >= j22 && k < k22) {
                    if (k20 < i) {
                        i = k20;
                        l = k21;
                    }
                    if (k20 > j) {
                        j = k20;
                        j24 = k21;
                    }
                    k20 += i21;
                    k21 += i22;
                }
                
                if (k >= l23 && k < i24) {
                    if (l22 < i) {
                        i = l22;
                        l = j23;
                    }
                    if (l22 > j) {
                        j = l22;
                        j24 = j23;
                    }
                    l22 += i23;
                    j23 += k23;
                }
                
                Scanline cameraVariables_7 = scanlines[k];
                cameraVariables_7.startX = i;
                cameraVariables_7.endX = j;
                cameraVariables_7.startS = l;
                cameraVariables_7.endS = j24;
            }

            if (minY < baseY - clipY) {
                minY = baseY - clipY;
            }
            
        } else {
            
            maxY = minY = planeY[0] += baseY;
            for (k = 1; k < plane; k++) {
                int i2;
                if ((i2 = planeY[k] += baseY) < minY) {
                    minY = i2;
                } else if (i2 > maxY) {
                    maxY = i2;
                }
            }

            if (minY < baseY - clipY) {
                minY = baseY - clipY;
            }
            
            if (maxY >= baseY + clipY) {
                maxY = (baseY + clipY) - 1;
            }
            
            if (minY >= maxY) {
                return;
            }
            
            for (k = minY; k < maxY; k++) {
                Scanline scanline = scanlines[k];
                scanline.startX = 0xa0000;
                scanline.endX = 0xfff60000;
            }

            int j2 = plane - 1;
            int i3 = planeY[0];
            int i4 = planeY[j2];
            
            if (i3 < i4) {
                
                int i5 = planeX[0] << 8;
                int j6 = (planeX[j2] - planeX[0] << 8) / (i4 - i3);
                int l7 = vertexShade[0] << 8;
                int j9 = (vertexShade[j2] - vertexShade[0] << 8) / (i4 - i3);
                
                if (i3 < 0) {
                    i5 -= j6 * i3;
                    l7 -= j9 * i3;
                    i3 = 0;
                }
                
                if (i4 > maxY) {
                    i4 = maxY;
                }
                
                for (k = i3; k <= i4; k++) {
                    Scanline cameraVariables_2 = scanlines[k];
                    cameraVariables_2.startX = cameraVariables_2.endX = i5;
                    cameraVariables_2.startS = cameraVariables_2.endS = l7;
                    i5 += j6;
                    l7 += j9;
                }

            } else if (i3 > i4) {
                
                int j5 = planeX[j2] << 8;
                int k6 = (planeX[0] - planeX[j2] << 8) / (i3 - i4);
                int i8 = vertexShade[j2] << 8;
                int k9 = (vertexShade[0] - vertexShade[j2] << 8) / (i3 - i4);
                
                if (i4 < 0) {
                    j5 -= k6 * i4;
                    i8 -= k9 * i4;
                    i4 = 0;
                }
                
                if (i3 > maxY) {
                    i3 = maxY;
                }
                
                for (k = i4; k <= i3; k++) {
                    Scanline cameraVariables_3 = scanlines[k];
                    cameraVariables_3.startX = cameraVariables_3.endX = j5;
                    cameraVariables_3.startS = cameraVariables_3.endS = i8;
                    j5 += k6;
                    i8 += k9;
                }
            }
            
            for (k = 0; k < j2; k++) {
                
                int k5 = k + 1;
                int j3 = planeY[k];
                int j4 = planeY[k5];
                
                if (j3 < j4) {
                    
                    int l6 = planeX[k] << 8;
                    int j8 = (planeX[k5] - planeX[k] << 8) / (j4 - j3);
                    int l9 = vertexShade[k] << 8;
                    int l10 = (vertexShade[k5] - vertexShade[k] << 8) / (j4 - j3);
                    
                    if (j3 < 0) {
                        l6 -= j8 * j3;
                        l9 -= l10 * j3;
                        j3 = 0;
                    }
                    
                    if (j4 > maxY) {
                        j4 = maxY;
                    }
                    
                    for (int l11 = j3; l11 <= j4; l11++) {
                        
                        Scanline cameraVariables_4 = scanlines[l11];
                        if (l6 < cameraVariables_4.startX) {
                            cameraVariables_4.startX = l6;
                            cameraVariables_4.startS = l9;
                        }
                        
                        if (l6 > cameraVariables_4.endX) {
                            cameraVariables_4.endX = l6;
                            cameraVariables_4.endS = l9;
                        }
                        
                        l6 += j8;
                        l9 += l10;
                    }

                } else if (j3 > j4) {
                    
                    int i7 = planeX[k5] << 8;
                    int k8 = (planeX[k] - planeX[k5] << 8) / (j3 - j4);
                    int i10 = vertexShade[k5] << 8;
                    int i11 = (vertexShade[k] - vertexShade[k5] << 8) / (j3 - j4);
                    
                    if (j4 < 0) {
                        i7 -= k8 * j4;
                        i10 -= i11 * j4;
                        j4 = 0;
                    }
                    
                    if (j3 > maxY) {
                        j3 = maxY;
                    }
                    
                    for (int i12 = j4; i12 <= j3; i12++) {
                        
                        Scanline cameraVariables_5 = scanlines[i12];
                        
                        if (i7 < cameraVariables_5.startX) {
                            cameraVariables_5.startX = i7;
                            cameraVariables_5.startS = i10;
                        }
                        
                        if (i7 > cameraVariables_5.endX) {
                            cameraVariables_5.endX = i7;
                            cameraVariables_5.endS = i10;
                        }
                        
                        i7 += k8;
                        i10 += i11;
                    }
                }
            }

            if (minY < baseY - clipY) {
                minY = baseY - clipY;
            }
        }

        /*
         * Mouse Picking
         */
        
        int mouseX = mousePicker.getMouseX();
        int mouseY = mousePicker.getMouseY();
        
        if (mouseY >= minY && mouseY < maxY) {
            Scanline scanline = scanlines[mouseY];
            if (mouseX >= scanline.startX >> 8 &&
                    mouseX <= scanline.endX >> 8 &&
                    scanline.startX <= scanline.endX &&
                    !gameModel.unpickable) {
                mousePicker.add(gameModel, faceId);
            }
        }
    }

    private void rasterize(Canvas canvas, int numFaces, int vertexX[], int vertexY[], int vertexZ[], int textureId, Model gameModel) {
        
        if (textureId == -2) {
            // Transparent
            return;
        }
        
        if (textureId >= 0) {
            
            if (textureId >= Resources.textures.length) {
                // Invalid texture
                textureId = 0;
            }
            
            Resources.prepareTexture(textureId);
            Texture tex = Resources.textures[textureId];
            
            int x1 = vertexX[0];
            int y1 = vertexY[0];
            int z1 = vertexZ[0];
            int dx1 = x1 - vertexX[1];
            int dy1 = y1 - vertexY[1];
            int dz1 = z1 - vertexZ[1];
            int dx2 = vertexX[numFaces - 1] - x1;
            int dy2 = vertexY[numFaces - 1] - y1;
            int dz2 = vertexZ[numFaces - 1] - z1;
            
            /*
             * Large textures (>128 pixels wide)
             */
            
            if (tex.isLarge()) {
                
                int l9 = dx2 * y1 - dy2 * x1 << 12;
                int k10 = dy2 * z1 - dz2 * y1 << (5 - viewDistance) + 7 + 4;
                int i11 = dz2 * x1 - dx2 * z1 << (5 - viewDistance) + 7;
                int k11 = dx1 * y1 - dy1 * x1 << 12;
                int i12 = dy1 * z1 - dz1 * y1 << (5 - viewDistance) + 7 + 4;
                int k12 = dz1 * x1 - dx1 * z1 << (5 - viewDistance) + 7;
                int i13 = dy1 * dx2 - dx1 * dy2 << 5;
                int k13 = dz1 * dy2 - dy1 * dz2 << (5 - viewDistance) + 4;
                int i14 = dx1 * dz2 - dz1 * dx2 >> viewDistance - 5;
                int k14 = k10 >> 4;
                int i15 = i12 >> 4;
                int k15 = k13 >> 4;
                int i16 = minY - baseY;
                int i17 = baseX + minY * width;
                l9 += i11 * i16;
                k11 += k12 * i16;
                i13 += i14 * i16;
                
                /*
                 * Translucent textures
                 */
                
                if (gameModel.textureTranslucent) {
                    for (int i = minY; i < maxY; i++) {
                        Scanline scanline = scanlines[i];
                        int scanlineStartX = scanline.startX >> 8;
                        int k17 = scanline.endX >> 8;
                        int k20 = k17 - scanlineStartX;
                        if (k20 <= 0) {
                            l9 += i11;
                            k11 += k12;
                            i13 += i14;
                            i17 += width;
                        } else {
                            int i22 = scanline.startS;
                            int k23 = (scanline.endS - i22) / k20;
                            if (scanlineStartX < -clipX) {
                                i22 += (-clipX - scanlineStartX) * k23;
                                scanlineStartX = -clipX;
                                k20 = k17 - scanlineStartX;
                            }
                            if (k17 > clipX) {
                                int l17 = clipX;
                                k20 = l17 - scanlineStartX;
                            }
                            canvas.renderScanline_LargeTranslucentTexture(tex.pixels, 0, 0, l9 + k14 * scanlineStartX, k11 + i15 * scanlineStartX,
                                    i13 + k15 * scanlineStartX, k10, i12, k13, k20, i17 + scanlineStartX, i22, k23 << 2);
                            l9 += i11;
                            k11 += k12;
                            i13 += i14;
                            i17 += width;
                        }
                    }

                    return;
                }

                /*
                 * Solid textures
                 */
                
                if (!tex.hasTransparency()) {
                    for (int i = minY; i < maxY; i++) {
                        Scanline scanline = scanlines[i];
                        int scanlineStartX = scanline.startX >> 8;
                        int i18 = scanline.endX >> 8;
                        int l20 = i18 - scanlineStartX;
                        if (l20 <= 0) {
                            l9 += i11;
                            k11 += k12;
                            i13 += i14;
                            i17 += width;
                        } else {
                            int j22 = scanline.startS;
                            int l23 = (scanline.endS - j22) / l20;
                            if (scanlineStartX < -clipX) {
                                j22 += (-clipX - scanlineStartX) * l23;
                                scanlineStartX = -clipX;
                                l20 = i18 - scanlineStartX;
                            }
                            if (i18 > clipX) {
                                int j18 = clipX;
                                l20 = j18 - scanlineStartX;
                            }
                            canvas.renderScanline_LargeTexture(
                                    tex.pixels,
                                    0,
                                    0,
                                    l9 + k14 * scanlineStartX,
                                    k11 + i15 * scanlineStartX,
                                    i13 + k15 * scanlineStartX,
                                    k10,
                                    i12,
                                    k13,
                                    l20,
                                    i17 + scanlineStartX,
                                    j22,
                                    l23 << 2);
                            l9 += i11;
                            k11 += k12;
                            i13 += i14;
                            i17 += width;
                        }
                    }

                    return;
                }

                /*
                 * Textures with transparency
                 */
                
                for (int i = minY; i < maxY; i++) {
                    Scanline scanline = scanlines[i];
                    int scanlineStartX = scanline.startX >> 8;
                    int k18 = scanline.endX >> 8;
                    int i21 = k18 - scanlineStartX;
                    if (i21 <= 0) {
                        l9 += i11;
                        k11 += k12;
                        i13 += i14;
                        i17 += width;
                    } else {
                        int k22 = scanline.startS;
                        int i24 = (scanline.endS - k22) / i21;
                        if (scanlineStartX < -clipX) {
                            k22 += (-clipX - scanlineStartX) * i24;
                            scanlineStartX = -clipX;
                            i21 = k18 - scanlineStartX;
                        }
                        if (k18 > clipX) {
                            int l18 = clipX;
                            i21 = l18 - scanlineStartX;
                        }
                        canvas.renderScanline_LargeTextureWithTransparency(0, 0, 0, tex.pixels, l9 + k14 * scanlineStartX,
                                k11 + i15 * scanlineStartX, i13 + k15 * scanlineStartX, k10, i12, k13, i21, i17 + scanlineStartX, k22, i24);
                        l9 += i11;
                        k11 += k12;
                        i13 += i14;
                        i17 += width;
                    }
                }

                return;
            }

            /*
             * Small textures (<128 pixels wide)
             */
            
            int i10 = dx2 * y1 - dy2 * x1 << 11;
            int l10 = dy2 * z1 - dz2 * y1 << (5 - viewDistance) + 6 + 4;
            int j11 = dz2 * x1 - dx2 * z1 << (5 - viewDistance) + 6;
            int l11 = dx1 * y1 - dy1 * x1 << 11;
            int j12 = dy1 * z1 - dz1 * y1 << (5 - viewDistance) + 6 + 4;
            int l12 = dz1 * x1 - dx1 * z1 << (5 - viewDistance) + 6;
            int j13 = dy1 * dx2 - dx1 * dy2 << 5;
            int l13 = dz1 * dy2 - dy1 * dz2 << (5 - viewDistance) + 4;
            int j14 = dx1 * dz2 - dz1 * dx2 >> viewDistance - 5;
            int l14 = l10 >> 4;
            int j15 = j12 >> 4;
            int l15 = l13 >> 4;
            int j16 = minY - baseY;
            int l16 = width;
            int j17 = baseX + minY * l16;
            i10 += j11 * j16;
            l11 += l12 * j16;
            j13 += j14 * j16;

            /*
             * Translucent textures
             */
            
            if (gameModel.textureTranslucent) {
                for (int i = minY; i < maxY; i++) {
                    Scanline scanline = scanlines[i];
                    int scanlineStartX = scanline.startX >> 8;
                    int i19 = scanline.endX >> 8;
                    int j21 = i19 - scanlineStartX;
                    if (j21 <= 0) {
                        i10 += j11;
                        l11 += l12;
                        j13 += j14;
                        j17 += l16;
                    } else {
                        int l22 = scanline.startS;
                        int j24 = (scanline.endS - l22) / j21;
                        if (scanlineStartX < -clipX) {
                            l22 += (-clipX - scanlineStartX) * j24;
                            scanlineStartX = -clipX;
                            j21 = i19 - scanlineStartX;
                        }
                        if (i19 > clipX) {
                            int j19 = clipX;
                            j21 = j19 - scanlineStartX;
                        }
                        canvas.renderScanline_SmallTranslucentTexture(tex.pixels, 0, 0, i10 + l14 * scanlineStartX, l11 + j15 * scanlineStartX,
                                j13 + l15 * scanlineStartX, l10, j12, l13, j21, j17 + scanlineStartX, l22, j24);
                        i10 += j11;
                        l11 += l12;
                        j13 += j14;
                        j17 += l16;
                    }
                }

                return;
            }

            /*
             * Solid textures
             */
            
            if (!tex.hasTransparency()) {
                for (int i = minY; i < maxY; i++) {
                    Scanline scanline = scanlines[i];
                    int scanlineStartX = scanline.startX >> 8;
                    int k19 = scanline.endX >> 8;
                    int k21 = k19 - scanlineStartX;
                    if (k21 <= 0) {
                        i10 += j11;
                        l11 += l12;
                        j13 += j14;
                        j17 += l16;
                        continue;
                    }
                    
                    int i23 = scanline.startS;
                    int k24 = (scanline.endS - i23) / k21;
                    if (scanlineStartX < -clipX) {
                        i23 += (-clipX - scanlineStartX) * k24;
                        scanlineStartX = -clipX;
                        k21 = k19 - scanlineStartX;
                    }
                    if (k19 > clipX) {
                        int l19 = clipX;
                        k21 = l19 - scanlineStartX;
                    }
                    
                    canvas.renderScanline_SmallTexture(
                            tex.pixels,
                            0,
                            0,
                            i10 + l14 * scanlineStartX,
                            l11 + j15 * scanlineStartX,
                            j13 + l15 * scanlineStartX,
                            l10,
                            j12,
                            l13,
                            k21,
                            j17 + scanlineStartX,
                            i23,
                            k24);
                        
                    i10 += j11;
                    l11 += l12;
                    j13 += j14;
                    j17 += l16;
                }
                return;
            }

            /*
             * Textures with transparency
             */
            
            for (int i = minY; i < maxY; i++) {
                Scanline scanline = scanlines[i];
                int scanlineStartX = scanline.startX >> 8;
                int i20 = scanline.endX >> 8;
                int l21 = i20 - scanlineStartX;
                if (l21 <= 0) {
                    i10 += j11;
                    l11 += l12;
                    j13 += j14;
                    j17 += l16;
                } else {
                    int j23 = scanline.startS;
                    int l24 = (scanline.endS - j23) / l21;
                    if (scanlineStartX < -clipX) {
                        j23 += (-clipX - scanlineStartX) * l24;
                        scanlineStartX = -clipX;
                        l21 = i20 - scanlineStartX;
                    }
                    if (i20 > clipX) {
                        int j20 = clipX;
                        l21 = j20 - scanlineStartX;
                    }
                    canvas.renderScanline_SmallTextureWithTransparency(0, 0, 0, tex.pixels, i10 + l14 * scanlineStartX,
                            l11 + j15 * scanlineStartX, j13 + l15 * scanlineStartX, l10, j12, l13, l21, j17 + scanlineStartX, j23, l24);
                    i10 += j11;
                    l11 += l12;
                    j13 += j14;
                    j17 += l16;
                }
            }

            return;
        }
        
        for (int j1 = 0; j1 < rampCount; j1++) {
            if (gradientBase[j1] == textureId) {
                currentGradientRamps = gradientRamps[j1];
                break;
            }
            if (j1 == rampCount - 1) {
                int l1 = (int) (Math.random() * rampCount);
                gradientBase[l1] = textureId;
                textureId = -1 - textureId;
                int k2 = (textureId >> 10 & 0x1f) * 8;
                int j3 = (textureId >> 5 & 0x1f) * 8;
                int l3 = (textureId & 0x1f) * 8;
                for (int j4 = 0; j4 < 256; j4++) {
                    int j6 = j4 * j4;
                    int k7 = (k2 * j6) / 0x10000;
                    int l8 = (j3 * j6) / 0x10000;
                    int j10 = (l3 * j6) / 0x10000;
                    gradientRamps[l1][255 - j4] = (k7 << 16) + (l8 << 8) + j10;
                }

                currentGradientRamps = gradientRamps[l1];
            }
        }

        int l2 = baseX + minY * width;
        
        if (gameModel.transparent) {
            for (int i = minY; i < maxY; i++) {
                Scanline scanline = scanlines[i];
                int scanlineStartX = scanline.startX >> 8;
                int k4 = scanline.endX >> 8;
                int k6 = k4 - scanlineStartX;
                if (k6 <= 0) {
                    l2 += width;
                } else {
                    int l7 = scanline.startS;
                    int i9 = (scanline.endS - l7) / k6;
                    if (scanlineStartX < -clipX) {
                        l7 += (-clipX - scanlineStartX) * i9;
                        scanlineStartX = -clipX;
                        k6 = k4 - scanlineStartX;
                    }
                    if (k4 > clipX) {
                        int l4 = clipX;
                        k6 = l4 - scanlineStartX;
                    }
                    canvas.renderScanline_TranslucentGradient(-k6, l2 + scanlineStartX, 0, currentGradientRamps, l7, i9);
                    l2 += width;
                }
            }
            return;
        }
        
        for (int i = minY; i < maxY; i++) {
            Scanline scanline = scanlines[i];
            int scanlineStartX = scanline.startX >> 8;
            int k5 = scanline.endX >> 8;
            int i7 = k5 - scanlineStartX;
            if (i7 <= 0) {
                l2 += width;
            } else {
                int j8 = scanline.startS;
                int k9 = (scanline.endS - j8) / i7;
                if (scanlineStartX < -clipX) {
                    j8 += (-clipX - scanlineStartX) * k9;
                    scanlineStartX = -clipX;
                    i7 = k5 - scanlineStartX;
                }
                if (k5 > clipX) {
                    int l5 = clipX;
                    i7 = l5 - scanlineStartX;
                }
                
                canvas.renderScanline_Gradient(-i7, l2 + scanlineStartX, 0, currentGradientRamps, j8, k9);
                    
                l2 += width;
            }
        }
    }

    private void initialisePolygon3d(int i) {
        Polygon polygon = visiblePolygons[i];
        Model gameModel = polygon.gameModel;
        int face = polygon.face;
        int faceVertices[] = gameModel.faceVertices[face];
        int faceNumVertices = gameModel.faceNumVertices[face];
        int faceCameraNormalScale = gameModel.normalScale[face];
        int vcx = gameModel.projectVertexX[faceVertices[0]];
        int vcy = gameModel.projectVertexY[faceVertices[0]];
        int vcz = gameModel.projectVertexZ[faceVertices[0]];
        int vcx1 = gameModel.projectVertexX[faceVertices[1]] - vcx;
        int vcy1 = gameModel.projectVertexY[faceVertices[1]] - vcy;
        int vcz1 = gameModel.projectVertexZ[faceVertices[1]] - vcz;
        int vcx2 = gameModel.projectVertexX[faceVertices[2]] - vcx;
        int vcy2 = gameModel.projectVertexY[faceVertices[2]] - vcy;
        int vcz2 = gameModel.projectVertexZ[faceVertices[2]] - vcz;
        int k3 = vcy1 * vcz2 - vcy2 * vcz1;
        int l3 = vcz1 * vcx2 - vcz2 * vcx1;
        int i4 = vcx1 * vcy2 - vcx2 * vcy1;
        if (faceCameraNormalScale == -1) {
            faceCameraNormalScale = 0;
            for (; k3 > 25000 || l3 > 25000 || i4 > 25000 || k3 < -25000 || l3 < -25000 || i4 < -25000; i4 >>= 1) {
                faceCameraNormalScale++;
                k3 >>= 1;
                l3 >>= 1;
            }

            gameModel.normalScale[face] = faceCameraNormalScale;
            gameModel.normalMagnitude[face] = (int) (normalMagnitude * Math.sqrt(k3 * k3 + l3 * l3 + i4 * i4));
        } else {
            k3 >>= faceCameraNormalScale;
            l3 >>= faceCameraNormalScale;
            i4 >>= faceCameraNormalScale;
        }
        polygon.visibility = vcx * k3 + vcy * l3 + vcz * i4;
        polygon.normalX = k3;
        polygon.normalY = l3;
        polygon.normalZ = i4;
        int j4 = gameModel.projectVertexZ[faceVertices[0]];
        int k4 = j4;
        int l4 = gameModel.vertexViewX[faceVertices[0]];
        int i5 = l4;
        int j5 = gameModel.vertexViewY[faceVertices[0]];
        int k5 = j5;
        for (int l5 = 1; l5 < faceNumVertices; l5++) {
            int i1 = gameModel.projectVertexZ[faceVertices[l5]];
            if (i1 > k4) {
                k4 = i1;
            } else if (i1 < j4) {
                j4 = i1;
            }
            i1 = gameModel.vertexViewX[faceVertices[l5]];
            if (i1 > i5) {
                i5 = i1;
            } else if (i1 < l4) {
                l4 = i1;
            }
            i1 = gameModel.vertexViewY[faceVertices[l5]];
            if (i1 > k5) {
                k5 = i1;
            } else if (i1 < j5) {
                j5 = i1;
            }
        }

        polygon.minZ = j4;
        polygon.maxZ = k4;
        polygon.minPlaneX = l4;
        polygon.maxPlaneX = i5;
        polygon.minPlaneY = j5;
        polygon.maxPlaneY = k5;
    }

    private void initialisePolygon2d(int i) {
        Polygon polygon = visiblePolygons[i];
        Model gameModel = polygon.gameModel;
        int face = polygon.face;
        int faceVertices[] = gameModel.faceVertices[face];
        int l = 0;
        int i1 = 0;
        int j1 = 1;
        int vx = gameModel.projectVertexX[faceVertices[0]];
        int vy = gameModel.projectVertexY[faceVertices[0]];
        int vz = gameModel.projectVertexZ[faceVertices[0]];
        gameModel.normalMagnitude[face] = 1;
        gameModel.normalScale[face] = 0;
        polygon.visibility = vx * l + vy * i1 + vz * j1;
        polygon.normalX = l;
        polygon.normalY = i1;
        polygon.normalZ = j1;
        int j2 = gameModel.projectVertexZ[faceVertices[0]];
        int k2 = j2;
        int l2 = gameModel.vertexViewX[faceVertices[0]];
        int i3 = l2;
        if (gameModel.vertexViewX[faceVertices[1]] < l2) {
            l2 = gameModel.vertexViewX[faceVertices[1]];
        } else {
            i3 = gameModel.vertexViewX[faceVertices[1]];
        }
        int j3 = gameModel.vertexViewY[faceVertices[1]];
        int k3 = gameModel.vertexViewY[faceVertices[0]];
        int k = gameModel.projectVertexZ[faceVertices[1]];
        if (k > k2) {
            k2 = k;
        } else if (k < j2) {
            j2 = k;
        }
        k = gameModel.vertexViewX[faceVertices[1]];
        if (k > i3) {
            i3 = k;
        } else if (k < l2) {
            l2 = k;
        }
        k = gameModel.vertexViewY[faceVertices[1]];
        if (k > k3) {
            k3 = k;
        } else if (k < j3) {
            j3 = k;
        }
        polygon.minZ = j2;
        polygon.maxZ = k2;
        polygon.minPlaneX = l2 - 20;
        polygon.maxPlaneX = i3 + 20;
        polygon.minPlaneY = j3;
        polygon.maxPlaneY = k3;
    }

    private boolean arePolygonsSeparate(Polygon polygon1, Polygon polygon2) {
        if (polygon1.minPlaneX >= polygon2.maxPlaneX) {
            return true;
        }
        if (polygon2.minPlaneX >= polygon1.maxPlaneX) {
            return true;
        }
        if (polygon1.minPlaneY >= polygon2.maxPlaneY) {
            return true;
        }
        if (polygon2.minPlaneY >= polygon1.maxPlaneY) {
            return true;
        }
        if (polygon1.minZ >= polygon2.maxZ) {
            return true;
        }
        if (polygon2.minZ > polygon1.maxZ) {
            return false;
        }
        Model gameModel = polygon1.gameModel;
        Model model_1 = polygon2.gameModel;
        int i = polygon1.face;
        int j = polygon2.face;
        int ai[] = gameModel.faceVertices[i];
        int ai1[] = model_1.faceVertices[j];
        int k = gameModel.faceNumVertices[i];
        int l = model_1.faceNumVertices[j];
        int k2 = model_1.projectVertexX[ai1[0]];
        int l2 = model_1.projectVertexY[ai1[0]];
        int i3 = model_1.projectVertexZ[ai1[0]];
        int j3 = polygon2.normalX;
        int k3 = polygon2.normalY;
        int l3 = polygon2.normalZ;
        int i4 = model_1.normalMagnitude[j];
        int j4 = polygon2.visibility;
        boolean flag = false;
        for (int k4 = 0; k4 < k; k4++) {
            int i1 = ai[k4];
            int i2 = (k2 - gameModel.projectVertexX[i1]) * j3 + (l2 - gameModel.projectVertexY[i1]) * k3
                    + (i3 - gameModel.projectVertexZ[i1]) * l3;
            if ((i2 >= -i4 || j4 >= 0) && (i2 <= i4 || j4 <= 0)) {
                continue;
            }
            flag = true;
            break;
        }

        if (!flag) {
            return true;
        }
        k2 = gameModel.projectVertexX[ai[0]];
        l2 = gameModel.projectVertexY[ai[0]];
        i3 = gameModel.projectVertexZ[ai[0]];
        j3 = polygon1.normalX;
        k3 = polygon1.normalY;
        l3 = polygon1.normalZ;
        i4 = gameModel.normalMagnitude[i];
        j4 = polygon1.visibility;
        flag = false;
        for (int l4 = 0; l4 < l; l4++) {
            int j1 = ai1[l4];
            int j2 = (k2 - model_1.projectVertexX[j1]) * j3 + (l2 - model_1.projectVertexY[j1]) * k3
                    + (i3 - model_1.projectVertexZ[j1]) * l3;
            if ((j2 >= -i4 || j4 <= 0) && (j2 <= i4 || j4 >= 0)) {
                continue;
            }
            flag = true;
            break;
        }

        if (!flag) {
            return true;
        }
        int ai2[];
        int ai3[];
        if (k == 2) {
            ai2 = new int[4];
            ai3 = new int[4];
            int i5 = ai[0];
            int k1 = ai[1];
            ai2[0] = gameModel.vertexViewX[i5] - 20;
            ai2[1] = gameModel.vertexViewX[k1] - 20;
            ai2[2] = gameModel.vertexViewX[k1] + 20;
            ai2[3] = gameModel.vertexViewX[i5] + 20;
            ai3[0] = ai3[3] = gameModel.vertexViewY[i5];
            ai3[1] = ai3[2] = gameModel.vertexViewY[k1];
        } else {
            ai2 = new int[k];
            ai3 = new int[k];
            for (int j5 = 0; j5 < k; j5++) {
                int i6 = ai[j5];
                ai2[j5] = gameModel.vertexViewX[i6];
                ai3[j5] = gameModel.vertexViewY[i6];
            }

        }
        int ai4[];
        int ai5[];
        if (l == 2) {
            ai4 = new int[4];
            ai5 = new int[4];
            int k5 = ai1[0];
            int l1 = ai1[1];
            ai4[0] = model_1.vertexViewX[k5] - 20;
            ai4[1] = model_1.vertexViewX[l1] - 20;
            ai4[2] = model_1.vertexViewX[l1] + 20;
            ai4[3] = model_1.vertexViewX[k5] + 20;
            ai5[0] = ai5[3] = model_1.vertexViewY[k5];
            ai5[1] = ai5[2] = model_1.vertexViewY[l1];
        } else {
            ai4 = new int[l];
            ai5 = new int[l];
            for (int l5 = 0; l5 < l; l5++) {
                int j6 = ai1[l5];
                ai4[l5] = model_1.vertexViewX[j6];
                ai5[l5] = model_1.vertexViewY[j6];
            }

        }
        return !intersect(ai2, ai3, ai4, ai5);
    }

    private boolean heuristicPolygon(Polygon polygon, Polygon entity_1) {
        Model gameModel = polygon.gameModel;
        Model model_1 = entity_1.gameModel;
        int i = polygon.face;
        int j = entity_1.face;
        int ai[] = gameModel.faceVertices[i];
        int ai1[] = model_1.faceVertices[j];
        int k = gameModel.faceNumVertices[i];
        int l = model_1.faceNumVertices[j];
        int i2 = model_1.projectVertexX[ai1[0]];
        int j2 = model_1.projectVertexY[ai1[0]];
        int k2 = model_1.projectVertexZ[ai1[0]];
        int l2 = entity_1.normalX;
        int i3 = entity_1.normalY;
        int j3 = entity_1.normalZ;
        int k3 = model_1.normalMagnitude[j];
        int l3 = entity_1.visibility;
        boolean flag = false;
        for (int i4 = 0; i4 < k; i4++) {
            int i1 = ai[i4];
            int k1 = (i2 - gameModel.projectVertexX[i1]) * l2 + (j2 - gameModel.projectVertexY[i1]) * i3
                    + (k2 - gameModel.projectVertexZ[i1]) * j3;
            if ((k1 >= -k3 || l3 >= 0) && (k1 <= k3 || l3 <= 0)) {
                continue;
            }
            flag = true;
            break;
        }

        if (!flag) {
            return true;
        }
        i2 = gameModel.projectVertexX[ai[0]];
        j2 = gameModel.projectVertexY[ai[0]];
        k2 = gameModel.projectVertexZ[ai[0]];
        l2 = polygon.normalX;
        i3 = polygon.normalY;
        j3 = polygon.normalZ;
        k3 = gameModel.normalMagnitude[i];
        l3 = polygon.visibility;
        flag = false;
        for (int j4 = 0; j4 < l; j4++) {
            int j1 = ai1[j4];
            int l1 = (i2 - model_1.projectVertexX[j1]) * l2 + (j2 - model_1.projectVertexY[j1]) * i3
                    + (k2 - model_1.projectVertexZ[j1]) * j3;
            if ((l1 >= -k3 || l3 <= 0) && (l1 <= k3 || l3 >= 0)) {
                continue;
            }
            flag = true;
            break;
        }

        return !flag;
    }

    private static int method306(int i, int j, int k, int l, int i1) {
        if (l == j) {
            return i;
        } else {
            return i + ((k - i) * (i1 - j)) / (l - j);
        }
    }

    private static boolean method307(int i, int j, int k, int l, boolean flag) {
        if (flag && i <= k || i < k) {
            if (i > l) {
                return true;
            }
            if (j > k) {
                return true;
            }
            if (j > l) {
                return true;
            }
            return !flag;
        }
        if (i < l) {
            return true;
        }
        if (j < k) {
            return true;
        }
        if (j < l) {
            return true;
        }
        return flag;
    }

    private static boolean method308(int i, int j, int k, boolean flag) {
        if (flag && i <= k || i < k) {
            if (j > k) {
                return true;
            }
            return !flag;
        }
        if (j < k) {
            return true;
        } else {
            return flag;
        }
    }

    private static boolean intersect(int ai[], int ai1[], int ai2[], int ai3[]) {
        int i = ai.length;
        int j = ai2.length;
        byte byte0 = 0;
        int i20;
        int k20 = i20 = ai1[0];
        int k = 0;
        int j20;
        int l20 = j20 = ai3[0];
        int i1 = 0;
        for (int i21 = 1; i21 < i; i21++) {
            if (ai1[i21] < i20) {
                i20 = ai1[i21];
                k = i21;
            } else if (ai1[i21] > k20) {
                k20 = ai1[i21];
            }
        }

        for (int j21 = 1; j21 < j; j21++) {
            if (ai3[j21] < j20) {
                j20 = ai3[j21];
                i1 = j21;
            } else if (ai3[j21] > l20) {
                l20 = ai3[j21];
            }
        }

        if (j20 >= k20) {
            return false;
        }
        if (i20 >= l20) {
            return false;
        }
        int l;
        int j1;
        boolean flag;
        if (ai1[k] < ai3[i1]) {
            for (l = k; ai1[l] < ai3[i1]; l = (l + 1) % i) {
                ;
            }
            for (; ai1[k] < ai3[i1]; k = ((k - 1) + i) % i) {
                ;
            }
            int k1 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[i1]);
            int k6 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai3[i1]);
            int l10 = ai2[i1];
            flag = (k1 < l10) | (k6 < l10);
            if (method308(k1, k6, l10, flag)) {
                return true;
            }
            j1 = (i1 + 1) % j;
            i1 = ((i1 - 1) + j) % j;
            if (k == l) {
                byte0 = 1;
            }
        } else {
            for (j1 = i1; ai3[j1] < ai1[k]; j1 = (j1 + 1) % j) {
                ;
            }
            for (; ai3[i1] < ai1[k]; i1 = ((i1 - 1) + j) % j) {
                ;
            }
            int l1 = ai[k];
            int i11 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[k]);
            int l15 = method306(ai2[((j1 - 1) + j) % j], ai3[((j1 - 1) + j) % j], ai2[j1], ai3[j1], ai1[k]);
            flag = (l1 < i11) | (l1 < l15);
            if (method308(i11, l15, l1, !flag)) {
                return true;
            }
            l = (k + 1) % i;
            k = ((k - 1) + i) % i;
            if (i1 == j1) {
                byte0 = 2;
            }
        }
        while (byte0 == 0) {
            if (ai1[k] < ai1[l]) {
                if (ai1[k] < ai3[i1]) {
                    if (ai1[k] < ai3[j1]) {
                        int i2 = ai[k];
                        int l6 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai1[k]);
                        int j11 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[k]);
                        int i16 = method306(ai2[((j1 - 1) + j) % j], ai3[((j1 - 1) + j) % j], ai2[j1], ai3[j1], ai1[k]);
                        if (method307(i2, l6, j11, i16, flag)) {
                            return true;
                        }
                        k = ((k - 1) + i) % i;
                        if (k == l) {
                            byte0 = 1;
                        }
                    } else {
                        int j2 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[j1]);
                        int i7 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai3[j1]);
                        int k11 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai3[j1]);
                        int j16 = ai2[j1];
                        if (method307(j2, i7, k11, j16, flag)) {
                            return true;
                        }
                        j1 = (j1 + 1) % j;
                        if (i1 == j1) {
                            byte0 = 2;
                        }
                    }
                } else if (ai3[i1] < ai3[j1]) {
                    int k2 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[i1]);
                    int j7 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai3[i1]);
                    int l11 = ai2[i1];
                    int k16 = method306(ai2[((j1 - 1) + j) % j], ai3[((j1 - 1) + j) % j], ai2[j1], ai3[j1], ai3[i1]);
                    if (method307(k2, j7, l11, k16, flag)) {
                        return true;
                    }
                    i1 = ((i1 - 1) + j) % j;
                    if (i1 == j1) {
                        byte0 = 2;
                    }
                } else {
                    int l2 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[j1]);
                    int k7 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai3[j1]);
                    int i12 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai3[j1]);
                    int l16 = ai2[j1];
                    if (method307(l2, k7, i12, l16, flag)) {
                        return true;
                    }
                    j1 = (j1 + 1) % j;
                    if (i1 == j1) {
                        byte0 = 2;
                    }
                }
            } else if (ai1[l] < ai3[i1]) {
                if (ai1[l] < ai3[j1]) {
                    int i3 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai1[l]);
                    int l7 = ai[l];
                    int j12 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[l]);
                    int i17 = method306(ai2[((j1 - 1) + j) % j], ai3[((j1 - 1) + j) % j], ai2[j1], ai3[j1], ai1[l]);
                    if (method307(i3, l7, j12, i17, flag)) {
                        return true;
                    }
                    l = (l + 1) % i;
                    if (k == l) {
                        byte0 = 1;
                    }
                } else {
                    int j3 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[j1]);
                    int i8 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai3[j1]);
                    int k12 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai3[j1]);
                    int j17 = ai2[j1];
                    if (method307(j3, i8, k12, j17, flag)) {
                        return true;
                    }
                    j1 = (j1 + 1) % j;
                    if (i1 == j1) {
                        byte0 = 2;
                    }
                }
            } else if (ai3[i1] < ai3[j1]) {
                int k3 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[i1]);
                int j8 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai3[i1]);
                int l12 = ai2[i1];
                int k17 = method306(ai2[((j1 - 1) + j) % j], ai3[((j1 - 1) + j) % j], ai2[j1], ai3[j1], ai3[i1]);
                if (method307(k3, j8, l12, k17, flag)) {
                    return true;
                }
                i1 = ((i1 - 1) + j) % j;
                if (i1 == j1) {
                    byte0 = 2;
                }
            } else {
                int l3 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[j1]);
                int k8 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai3[j1]);
                int i13 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai3[j1]);
                int l17 = ai2[j1];
                if (method307(l3, k8, i13, l17, flag)) {
                    return true;
                }
                j1 = (j1 + 1) % j;
                if (i1 == j1) {
                    byte0 = 2;
                }
            }
        }
        while (byte0 == 1) {
            if (ai1[k] < ai3[i1]) {
                if (ai1[k] < ai3[j1]) {
                    int i4 = ai[k];
                    int j13 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[k]);
                    int i18 = method306(ai2[((j1 - 1) + j) % j], ai3[((j1 - 1) + j) % j], ai2[j1], ai3[j1], ai1[k]);
                    return method308(j13, i18, i4, !flag);
                }
                int j4 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[j1]);
                int l8 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai3[j1]);
                int k13 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai3[j1]);
                int j18 = ai2[j1];
                if (method307(j4, l8, k13, j18, flag)) {
                    return true;
                }
                j1 = (j1 + 1) % j;
                if (i1 == j1) {
                    byte0 = 0;
                }
            } else if (ai3[i1] < ai3[j1]) {
                int k4 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[i1]);
                int i9 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai3[i1]);
                int l13 = ai2[i1];
                int k18 = method306(ai2[((j1 - 1) + j) % j], ai3[((j1 - 1) + j) % j], ai2[j1], ai3[j1], ai3[i1]);
                if (method307(k4, i9, l13, k18, flag)) {
                    return true;
                }
                i1 = ((i1 - 1) + j) % j;
                if (i1 == j1) {
                    byte0 = 0;
                }
            } else {
                int l4 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[j1]);
                int j9 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai3[j1]);
                int i14 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai3[j1]);
                int l18 = ai2[j1];
                if (method307(l4, j9, i14, l18, flag)) {
                    return true;
                }
                j1 = (j1 + 1) % j;
                if (i1 == j1) {
                    byte0 = 0;
                }
            }
        }
        while (byte0 == 2) {
            if (ai3[i1] < ai1[k]) {
                if (ai3[i1] < ai1[l]) {
                    int i5 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[i1]);
                    int k9 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai3[i1]);
                    int j14 = ai2[i1];
                    return method308(i5, k9, j14, flag);
                }
                int j5 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai1[l]);
                int l9 = ai[l];
                int k14 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[l]);
                int i19 = method306(ai2[((j1 - 1) + j) % j], ai3[((j1 - 1) + j) % j], ai2[j1], ai3[j1], ai1[l]);
                if (method307(j5, l9, k14, i19, flag)) {
                    return true;
                }
                l = (l + 1) % i;
                if (k == l) {
                    byte0 = 0;
                }
            } else if (ai1[k] < ai1[l]) {
                int k5 = ai[k];
                int i10 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai1[k]);
                int l14 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[k]);
                int j19 = method306(ai2[((j1 - 1) + j) % j], ai3[((j1 - 1) + j) % j], ai2[j1], ai3[j1], ai1[k]);
                if (method307(k5, i10, l14, j19, flag)) {
                    return true;
                }
                k = ((k - 1) + i) % i;
                if (k == l) {
                    byte0 = 0;
                }
            } else {
                int l5 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai1[l]);
                int j10 = ai[l];
                int i15 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[l]);
                int k19 = method306(ai2[((j1 - 1) + j) % j], ai3[((j1 - 1) + j) % j], ai2[j1], ai3[j1], ai1[l]);
                if (method307(l5, j10, i15, k19, flag)) {
                    return true;
                }
                l = (l + 1) % i;
                if (k == l) {
                    byte0 = 0;
                }
            }
        }
        if (ai1[k] < ai3[i1]) {
            int i6 = ai[k];
            int j15 = method306(ai2[(i1 + 1) % j], ai3[(i1 + 1) % j], ai2[i1], ai3[i1], ai1[k]);
            int l19 = method306(ai2[((j1 - 1) + j) % j], ai3[((j1 - 1) + j) % j], ai2[j1], ai3[j1], ai1[k]);
            return method308(j15, l19, i6, !flag);
        }
        int j6 = method306(ai[(k + 1) % i], ai1[(k + 1) % i], ai[k], ai1[k], ai3[i1]);
        int k10 = method306(ai[((l - 1) + i) % i], ai1[((l - 1) + i) % i], ai[l], ai1[l], ai3[i1]);
        int k15 = ai2[i1];
        return method308(j6, k10, k15, flag);
    }

    public MousePicker getMousePicker() {
        return mousePicker;
    }
    
}
