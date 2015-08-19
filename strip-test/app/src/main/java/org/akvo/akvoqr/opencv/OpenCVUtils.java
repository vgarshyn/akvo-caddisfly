package org.akvo.akvoqr.opencv;

import android.graphics.Color;

import org.akvo.akvoqr.ResultActivity;
import org.akvo.akvoqr.ResultStripTestActivity;
import org.akvo.akvoqr.TestResult;
import org.akvo.akvoqr.detector.FinderPatternInfo;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;
import org.opencv.video.BackgroundSubtractorMOG;
import org.opencv.video.BackgroundSubtractorMOG2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by linda on 7/23/15.
 */
public class OpenCVUtils {

    public static Mat rotateImage(Mat src, RotatedRect rotatedRect)
    {
        Point[] rotatedRectPoints = new Point[4];
        rotatedRect.points(rotatedRectPoints);

        Mat rot_mat;
        Mat cropped = new Mat();

        /// Set the dst image the same type and size as src
        Mat warp_rotate_dst = Mat.zeros(src.rows(), src.cols(), src.type());

        double angle = rotatedRect.angle;
        Size rect_size = rotatedRect.size;
        // thanks to http://felix.abecassis.me/2011/10/opencv-rotation-deskewing/
        // we need to swap height and width if angle is lower than 45 degrees
        if (angle < -45.) {
            angle += 90.0;
            rect_size.set(new double[]{rect_size.height, rect_size.width});
        }
        // get the rotation matrix
        rot_mat = Imgproc.getRotationMatrix2D(rotatedRect.center, angle, 1.0);
        // perform the affine transformation
        Imgproc.warpAffine(src, warp_rotate_dst, rot_mat, src.size(), Imgproc.INTER_CUBIC);
        // crop the resulting image
        rect_size.set(new double[]{rect_size.width-2, rect_size.height-2});
        Imgproc.getRectSubPix(warp_rotate_dst, rect_size, rotatedRect.center, cropped);

        return cropped;
    }
    public static Mat perspectiveTransform(FinderPatternInfo info, Mat mbgra)
    {
        List<Point> srcList = new ArrayList<>();

        //coordinates for the rect (the finder pattern centers)
        srcList.add(new Point(info.getTopLeft().getX(),
                info.getTopLeft().getY()));
        srcList.add(new Point(info.getTopRight().getX(),
                info.getTopRight().getY()));
        srcList.add(new Point(info.getBottomLeft().getX(),
                info.getBottomLeft().getY()));
        srcList.add(new Point(info.getBottomRight().getX(),
                info.getBottomRight().getY()));

        System.out.println("***before sort:");
        System.out.println("***topleft: " + srcList.get(0).x + " ," + srcList.get(0).y);
        System.out.println("***topright: " + srcList.get(1).x + " ," + srcList.get(1).y);
        System.out.println("***bottomleft: " + srcList.get(2).x + " ," + srcList.get(2).y);
        System.out.println("***bottomright: " + srcList.get(3).x + ", " + srcList.get(3).y);

        //Sort the arraylist of finder patterns based on a comparison of the sum of x and y values. Lowest values come first,
        // so the result will be: top-left, bottom-left, top-right, bottom-right. Because top-left always has the lowest sum of x and y
        // and bottom-right always the highest
        Collections.sort(srcList, new PointComparator());

        System.out.println("***after sort:");
        System.out.println("***topleft: " + srcList.get(0).x +" ,"+ srcList.get(0).y);
        System.out.println("***bottomleft: " + srcList.get(1).x +" ,"+ srcList.get(1).y);
        System.out.println("***topright: " + srcList.get(2).x +" ,"+ srcList.get(2).y);
        System.out.println("***bottomright: "+ srcList.get(3).x + ", "+ srcList.get(3).y);

        //source quad
        //here we maintain the order: top-left, top-right, bottom-left, bottom-right
        Point[] srcQuad = new Point[4];
        srcQuad[0]=srcList.get(0);
        srcQuad[1]=srcList.get(2);
        srcQuad[2]=srcList.get(1);
        srcQuad[3]=srcList.get(3);
        //destination quad corresponding with srcQuad
        Point[] dstQuad = new Point[4];
        dstQuad[0] = new Point( 0,0 );
        dstQuad[1] = new Point( mbgra.cols() - 1, 0 );
        dstQuad[2] = new Point( 0, mbgra.rows() - 1 );
        dstQuad[3] = new Point(mbgra.cols()-1, mbgra.rows()-1);

        //srcQuad and destQuad to MatOfPoint2f objects, needed in perspective transform
        MatOfPoint2f srcMat2f = new MatOfPoint2f(srcQuad);
        MatOfPoint2f dstMat2f = new MatOfPoint2f(dstQuad);

        //make a destination mat for a warp
        Mat warp_dst = Mat.zeros(mbgra.rows(), mbgra.cols(), mbgra.type());

        //get a perspective transform matrix
        Mat warp_mat = Imgproc.getPerspectiveTransform(srcMat2f, dstMat2f);

        //do the warp
        Imgproc.warpPerspective(mbgra, warp_dst,warp_mat, warp_dst.size());

        return warp_dst;
    }
    public static Mat detectStrip(Mat striparea)
    {
        Mat dst = new Mat();
        List<Mat> channels = new ArrayList<>();

        Imgproc.cvtColor(striparea, dst, Imgproc.COLOR_RGB2Lab, 0);

        Imgproc.medianBlur(dst, dst, 11);

        Mat temp = dst.clone();
        for(int c=0; c<dst.cols()-11; c++) {
            Mat submat = dst.submat(0, dst.rows()-1, c, c+11).clone();

            Core.split(submat, channels);
            Core.MinMaxLocResult result = Core.minMaxLoc(channels.get(0));

//            System.out.println("***result L detect strip min val: " + result.minVal + " max val : " + result.maxVal);

            double treshold = result.minVal*1.25;

            Imgproc.threshold(channels.get(0), channels.get(0), treshold, 255, Imgproc.THRESH_BINARY);

            Core.merge(channels, submat);

            for(int sc=0;sc<submat.cols();sc++) {
                for (int sr = 0; sr < submat.rows(); sr++) {
                    double[] vals = submat.get(sr, sc);
                    for (int v = 0; v < vals.length; v++) {
                        temp.put(sr, c+sc, vals);
                    }
                }
            }
            submat.release();
        }

        dst = temp.clone();
        temp.release();

        Imgproc.medianBlur(dst,dst,11);
        Core.split(dst, channels);
        Core.inRange(channels.get(0), new Scalar(200, 0, 0), new Scalar(256, 255, 255), channels.get(0));

//        Imgproc.cvtColor(dst, range, Imgproc.COLOR_RGB2GRAY);
//        Imgproc.Canny(dst, range, 40, 120, 3, true);

        Imgproc.cvtColor(dst, dst, Imgproc.COLOR_Lab2RGB);

        ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        MatOfPoint innermostContours = new MatOfPoint();
        MatOfInt4 mContours = new MatOfInt4();
        MatOfPoint2f mMOP2f = new MatOfPoint2f();

        RotatedRect rotatedRect;

        Imgproc.findContours(channels.get(0), contours, mContours, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE, new Point(0, 0));
        double maxParent = -Double.MAX_VALUE;
        for (int x = 0; x < contours.size(); x++) {

            // we need the mMOP2f object for our rotated Rect
            contours.get(x).convertTo(mMOP2f, CvType.CV_32FC2);

            double areasize = Imgproc.contourArea(contours.get(x));

            System.out.println("***areasize contour strip: " + areasize);

//            Imgproc.drawContours(dst, contours, x, new Scalar(255, 2, 255, 255), 1);

            if(mContours.get(0,x)[3] >= 0)//has parent, inner (hole) contour of a closed edge
            {
                if(areasize > 10000)
                {

                    if(mContours.get(0,x)[3] > maxParent)
                    {
                        innermostContours = contours.get(x);
                        maxParent = mContours.get(0, x)[3];
                    }
                }

                //Imgproc.drawContours(striparea, contours, x-1, new Scalar(0, 0, 255, 255), -1);

            } else {
//                Imgproc.drawContours(dst, contours, x, new Scalar(0, 255, 0, 255), 1);
            }
        }

        List<Point> innermostList = innermostContours.toList();
        if(innermostList.size()>0) {

            //only needed for demo
//            Point point1 = new Point(OpenCVUtils.getMinX(innermostList), OpenCVUtils.getMinY(innermostList));
//            Point point2 = new Point(getMaxX(innermostList), getMinY(innermostList));
//            Point point3 = new Point(OpenCVUtils.getMaxX(innermostList), OpenCVUtils.getMaxY(innermostList));

//            System.out.println("*** innermostList 0 : " + innermostList.get(0).x + "," + innermostList.get(0).y);
//            System.out.println("*** innermostList topleft :" + point1.x + "," + point1.y);
//            System.out.println("*** innermostList topright :" + point2.x + "," + point2.y);
//            System.out.println("*** innermostList bottomleft :" + point3.x + "," + point3.y);

            double d = Imgproc.contourArea(innermostContours);
            System.out.println("***contour area innermost: " + d);

            //demo
//            Imgproc.drawContours(dst, contours, (int) maxParent + 1, new Scalar(0, 0, 255, 255), 3);
//            Core.rectangle(dst,point1, point3, new Scalar(255, 0, 0, 255), 2);

            rotatedRect = Imgproc.minAreaRect(mMOP2f);
            if(rotatedRect!=null) {

                //only needed for demo
//                Point[] rotPoints = new Point[4];
//                rotatedRect.points(rotPoints);

//                Core.line(dst, rotPoints[0], rotPoints[1], new Scalar(0, 0, 255, 255), 2);
//                Core.line(dst, rotPoints[1], rotPoints[2], new Scalar(0,0,255,255), 2);
//                Core.line(dst, rotPoints[2], rotPoints[3], new Scalar(0,0,255,255), 2);
//                Core.line(dst, rotPoints[3], rotPoints[0], new Scalar(0,0,255,255), 2);
                Mat rotated = rotateImage(striparea, rotatedRect);
                System.out.println("***rotated mat: " + CvType.typeToString(rotated.type()));

//                Mat hsv = new Mat();
//
//                Imgproc.cvtColor(rotated, hsv, Imgproc.COLOR_RGB2HSV);
//                temp = hsv.clone();
//
//                for(int c=24;c<temp.cols()-24;c+=1) {
//                    Mat submat = temp.submat(3, temp.rows() - 3, c - 24, c + 24);
//                    Core.split(submat, channels);
//                    Scalar mean = Core.mean(submat);
//                    Core.merge(channels, submat);
//
//                    for (int cc = 0; cc < submat.cols(); cc++) {
//                        for(int r=0;r<submat.rows();r++)
//                        {
//                            double[] vals = submat.get(r, cc);
//                            vals[0] = mean.val[0];
//                            vals[1] = mean.val[1];
//                            vals[2] = mean.val[2];
//
//                            for(int rr=0;rr<temp.rows();rr++)
//                                 temp.put(rr, c - 24 + cc, vals);
//                        }
//
//                    }
//
//
//                    submat.release();
//                }

//                for(int c=1;c<temp.cols();c++) {
//                    for (int rr = 0; rr < temp.rows(); rr++) {
//                        double[] hsvVals0 = temp.get(rr, c - 1);
//                        double[] hsvVals1 = temp.get(rr, c);
//                        if (Math.abs(hsvVals0[0] - hsvVals1[0]) > 150) {
//                            hsvVals0[0] = 0;
//                            hsvVals0[1] = 1;
//                            hsvVals0[2] = 0;
//                        }
//
//                        temp.put(rr, c, hsvVals0);
//                    }
//                }
//                hsv = temp.clone();
//                rotated = temp.clone();
//                temp.release();
//                Imgproc.cvtColor(hsv, rotated, Imgproc.COLOR_HSV2RGB);
                return rotated;
            }
        }

        return dst;
    }

    public static Mat detectStripColorBrandKnown(Mat src, StripTestBrand.brand brand)
    {
        StripTestBrand stripTestBrand = new StripTestBrand(brand);
        List<StripTestBrand.Patch> patches = stripTestBrand.getPatches();

        for (int i=0;i<patches.size();i++)
        {
            Point center = new Point(patches.get(i).center);
            Core.circle(src, center, 5, new Scalar(0,0,0,255), -1);
        }

        return src;
    }
    public static Point getLeft(List<Point> list)
    {
        double minX = Double.MAX_VALUE;
        Point left = new Point(0,0);
        for(Point p: list)
        {
            if(p.x < minX)
            {
                minX = p.x;
                left = p;
            }
        }
        return left;
    }
    public static Point getRight(List<Point> list)
    {
        double maxX = -Double.MAX_VALUE;
        Point right = new Point(0,0);
        for(Point p: list)
        {
            if(p.x > maxX)
            {
                maxX = p.x;
                right = p;
            }
        }
        return right;
    }
    public static Point getTop(List<Point> list)
    {
        double minY = Double.MAX_VALUE;
        Point top = new Point(0,0);
        for(Point p: list)
        {
            if(p.y < minY)
            {
                minY = p.y;
                top = p;
            }
        }
        return top;
    }
    public static Mat detectStripPatchesAdaptiveTresh(Mat strip)
    {

        Mat orig = strip.clone();
        Mat labMat = new Mat();
        Mat range = new Mat();
        List<Mat> channels = new ArrayList<>();
        List<Point> pts = new ArrayList<Point>();
        MatOfPoint2f mMOP2f = new MatOfPoint2f();
        //Treshold for both upper and lower
        MatOfInt matUpper = new MatOfInt();
        MatOfInt matLower = new MatOfInt();
        Mat grayUp = new Mat();
        Mat grayLow = new Mat();

        ResultActivity.stripColors.clear();

        Imgproc.cvtColor(strip, labMat, Imgproc.COLOR_RGB2Lab, 0);
        grayUp = labMat.clone();
        grayLow = labMat.clone();

        Mat temp = labMat.clone();
        double minChroma = Double.MAX_VALUE;
        Point minChromaPixPos = new Point();
        double[] minChromaLab = new double[3];

        // overall gaussian blur of image
        Imgproc.medianBlur(labMat,labMat, 5);

        //fill temp with average value for L, max values for a and b
        for(int c=0;c<labMat.cols();c++)
        {
            double sumL = 0;
            double sumA = 0;
            double sumB = 0;
            double minCh = Double.MAX_VALUE;
            Point minChPoint = new Point(0,0);
            double maxCh = -Double.MAX_VALUE;
            Point maxChPoint = new Point(0,0);
            int margin = 5;

            for(int r=margin;r<labMat.rows()-margin;r++)
            {
                double[] vals = labMat.get(r,c);
                sumL += vals[0];
                sumA += vals[1];
                sumB += vals[2];

                vals[1] = vals[1] - 128;
                vals[2] = vals[2] - 128;
                double ch = Math.sqrt(vals[1] * vals[1] + vals[2] * vals[2]);

                if(ch < minCh)
                {
                    minCh = ch;
                    minChPoint = new Point(c,r);
                }
                if(ch > maxCh)
                {
                    maxCh = ch;
                    maxChPoint = new Point(c,r);
                }
            }

            double avgL = sumL/(labMat.rows() - 2*margin);
            double avgA = sumA/(labMat.rows() - 2*margin);
            double avgB = sumB/(labMat.rows() - 2*margin);
//            System.out.println("***avgA for col. " + c + " = " + avgA);
//            System.out.println("***avgB for col. " + c + " = " + avgB);
//            System.out.println("***minCh for col. " + c + " = " + minCh);

            for(int rr=0; rr<labMat.rows(); rr++)
            {
                double[] vals = labMat.get(rr, c);
                double[] valsMinCh = labMat.get((int)minChPoint.y, (int)minChPoint.x);
                double[] valsMaxCh = labMat.get((int)maxChPoint.y, (int)maxChPoint.x);

                vals[0] = avgL;
                vals[1] = avgA;
                vals[2] = avgB;
                temp.put(rr, c, vals);
            }

        }

        Mat mat = temp.clone();
        int segmentWidth = (int) Math.round(mat.rows() * 2.5);
        int margin = 3;
        for(int i=margin; i<temp.cols()-margin; i+=1) {
            //calculate lower limit for submat
            int min = Math.min(i,segmentWidth/2);
            //calculate upper limit for submat
            int max = Math.min(temp.cols() - 2 * margin - i + 1, segmentWidth/2);
//            System.out.println("***min = " + min + " max = " + max);

            if(max <=0)
                break;

            //we averaged the L-values over each column, so we can limit the submat to just one row
            //submat = first row, i + segmentWidth(limited if end of strip is reached
            Mat submat = temp.submat(0, 10, i-min, i + max).clone();

            if (!submat.empty()) {

                Core.split(submat, channels);
                Core.MinMaxLocResult result = Core.minMaxLoc(channels.get(0));

//                System.out.println("***result maxVal = " + Math.round(result.maxVal) + ", minVal = " + Math.round(result.minVal)
//                        + " diff = " + Math.round(result.maxVal - result.minVal));

                double upperTreshold = 0.97 * result.maxVal;
                double lowerTreshold = result.maxVal - 0.5 * (result.maxVal - result.minVal);

//                Scalar mean = Core.mean(channels.get(0));
//                if(mean!=null && mean.val.length>0) {
//                    upperTreshold = 1.1 * mean.val[0];
//                    lowerTreshold = 0.9 * mean.val[0];
//                }
//                System.out.println("***result upper tresh = " + upperTreshold);
//                System.out.println("***result lower tresh = " + lowerTreshold);

                Imgproc.threshold(channels.get(0), matUpper, upperTreshold, 255, Imgproc.THRESH_BINARY);

                Mat mergeUp = new Mat();
                Core.merge(channels, mergeUp);
                for (int si = 0; si < mergeUp.cols(); si++) {
                    for (int j = 0; j < 1; j++) {

                        double[] vals = mergeUp.get(j, si);

                        for(int x=0;x<grayUp.rows();x++) {

                            grayUp.put(x, si + i - min, vals);
                        }
                    }
                }

                Imgproc.threshold(channels.get(0), matLower, lowerTreshold, 255, Imgproc.THRESH_BINARY);

                Mat mergeLow = new Mat();
                Core.merge(channels, mergeLow);
                for (int si = 0; si < mergeLow.cols(); si++) {
                    for (int j = 0; j < 1; j++) {

                        double[] vals = mergeLow.get(j, si);

                        for(int x=0;x<grayLow.rows();x++) {

                            grayLow.put(x, si + i - min, vals);
                        }
                    }
                }


                //(Bitwise OR) sets a bit to 1 if one or both of the corresponding bits in its operands are 1,
                // and to 0 if both of the corresponding bits are 0.
                //In other words, | returns one in all cases except where the corresponding bits of both operands are zero.
                //http://vipan.com/htdocs/bitwisehelp.html
                //Applied to upper- and lower mats, we retain only those pixels that are black in both
                Core.bitwise_or(matUpper, matLower, channels.get(0));

                Core.merge(channels, submat);

                for (int si = 0; si < submat.cols(); si++) {
                    for (int j = 0; j < 1; j++) {

                        double[] vals = submat.get(j, si);

                        for(int x=0;x<mat.rows();x++) {

                            mat.put(x, si + i - min, vals);
                        }
                    }
                }

                submat.release();
            }


        }
        range=mat.clone();
        labMat = mat.clone();

        Imgproc.cvtColor(labMat, labMat, Imgproc.COLOR_Lab2RGB);
        Imgproc.cvtColor(grayUp, grayUp, Imgproc.COLOR_Lab2RGB);
        Imgproc.cvtColor(grayLow, grayLow, Imgproc.COLOR_Lab2RGB);

        ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();

        MatOfInt4 hierarchy = new MatOfInt4();

        Core.split(range, channels);

        Core.MinMaxLocResult result0 = Core.minMaxLoc(channels.get(0));
        System.out.println("***channel L . min val: " + result0.minVal + " max val: " + result0.maxVal);
        Core.MinMaxLocResult result1 = Core.minMaxLoc(channels.get(1));
        System.out.println("***channel a . min val: " + result1.minVal + " max val: " + result1.maxVal);
        Core.MinMaxLocResult result2 = Core.minMaxLoc(channels.get(2));
        System.out.println("***channel b . min val: " + result2.minVal + " max val: " + result2.maxVal);

        Core.inRange(channels.get(0), new Scalar(0), new Scalar(1), range);
        Imgproc.findContours(range, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE, new Point(0, 0));

        List<Mat> outermostContoursList = new ArrayList<>();

        int numPatchesFound = 0;

        for (int x = 0; x < contours.size(); x++)
        {
            double areasize = Imgproc.contourArea(contours.get(x));

//            Imgproc.drawContours(labMat, contours, x, new Scalar(0, 250, 0, 255),2);

            if (hierarchy.get(0,x)!=null && hierarchy.get(0, x)[3] < 0)//has no parent, outer contour
            {
                System.out.println("***areasize: " + areasize);
                if (areasize > 250)
                {
                    double[] d = hierarchy.get(0, x);

                    for (double v : d)
                    {
                        System.out.println("*** value of outer contour  " +  x + ":  = " + v);
                    }
                    outermostContoursList.add(contours.get(x));
//                    Imgproc.drawContours(labMat, contours, x, new Scalar(255, 0, 0, 255), 1);
                }
            }

        }

        TestResult testResult = new TestResult();
        testResult.setOriginal(orig);

        if(outermostContoursList.size()>0) {
            numPatchesFound = 0;

            for (Mat outer : outermostContoursList) {
                //make square
//                outer.convertTo(mMOP2f, CvType.CV_32FC2);
//                Imgproc.approxPolyDP(mMOP2f, mMOP2f, 0.01 * Imgproc.arcLength(mMOP2f, true), true);
//                mMOP2f.convertTo(outer, CvType.CV_32S);


                if (outer.rows() > 3) {
                    System.out.println("***contour area outer: " + Imgproc.contourArea(outer));


                    if (Imgproc.contourArea(outer) > 200) {

                        Converters.Mat_to_vector_Point2f(outer, pts);

                        Point point1 = new Point(getMinX(pts), getMinY(pts));
                        Point point2 = new Point(getMaxX(pts), getMaxY(pts));
                        Core.rectangle(labMat, point1, point2, new Scalar(0, 255, 3, 255), 1);

                        pts = detectColor(labMat, pts);

                        Core.rectangle(labMat, pts.get(0), pts.get(1), new Scalar(0, 0, 0, 255), 2);

                        numPatchesFound++;
                    }
                }
            }
        }

        testResult.setNumPatchesFound(numPatchesFound);

//        testResult.setResultBitmap(grayUp, 0);
//        testResult.setResultBitmap(grayLow, 1);
        testResult.setResultBitmap(labMat, 2);
        ResultStripTestActivity.testResults.add(testResult);


        return strip;
    }

    public static Mat detectStripPatchesOTSUTresh(Mat strip)
    {
        Mat edges = new Mat();
        List<Mat> channels = new ArrayList<>();
        List<Point> pts = new ArrayList<Point>();
        MatOfPoint2f mMOP2f = new MatOfPoint2f();

        ResultActivity.stripColors.clear();
        Imgproc.cvtColor(strip, edges, Imgproc.COLOR_RGB2Lab, 0);

        Imgproc.GaussianBlur(edges, edges, new Size(5, 5), 0);
        Core.split(edges, channels);

        Core.MinMaxLocResult result0 = Core.minMaxLoc(channels.get(0));
        System.out.println("***channel L . min val: " + result0.minVal + " max val: " + result0.maxVal);
        Core.MinMaxLocResult result1 = Core.minMaxLoc(channels.get(1));
        System.out.println("***channel a . min val: " + result1.minVal + " max val: " + result1.maxVal);
        Core.MinMaxLocResult result2 = Core.minMaxLoc(channels.get(2));
        System.out.println("***channel b . min val: " + result2.minVal + " max val: " + result2.maxVal);

        Imgproc.threshold(channels.get(0), channels.get(0), 0, 128, Imgproc.THRESH_BINARY);
        Imgproc.threshold(channels.get(1), channels.get(1), 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
        Imgproc.threshold(channels.get(2), channels.get(2), 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);

        Core.merge(channels, edges);
        Imgproc.cvtColor(edges, edges, Imgproc.COLOR_Lab2RGB);

        ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        MatOfInt4 mContours = new MatOfInt4();

        Imgproc.findContours(channels.get(1), contours, mContours, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE, new Point(0, 0));
        Imgproc.findContours(channels.get(2), contours, mContours, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE, new Point(0, 0));

        for (int x = 0; x < contours.size(); x++) {

            //make square
            contours.get(x).convertTo(mMOP2f, CvType.CV_32FC2);
            Imgproc.approxPolyDP(mMOP2f, mMOP2f, 0.1 * Imgproc.arcLength(mMOP2f, true), true);
            mMOP2f.convertTo(contours.get(x), CvType.CV_32S);

            if(contours.get(x).rows() == 4) {
                if (Imgproc.contourArea(contours.get(x)) > 200) {

                    Converters.Mat_to_vector_Point2f(contours.get(x), pts);
                    detectColor(strip, pts);
                    Imgproc.drawContours(edges, contours, x, new Scalar(0,255,0, 255), 1);
                }
            }
        }


        return edges;
    }
    public static List<Point> detectColor(Mat rgb, List<Point> pts) {

        int left = getMinX(pts);
        int top = getMinY(pts);
        int maxX = getMaxX(pts);
        int maxY = getMaxY(pts);

        System.out.println("*** top: " + top);
        System.out.println("*** left: " + left);
        System.out.println("*** maxY: " + maxY);
        System.out.println("*** maxX: " + maxX);

        Mat sub = rgb.submat(top , top + (maxY - top), left, left + (maxX-left));

        Mat lab = new Mat();
        Imgproc.cvtColor(sub, lab, Imgproc.COLOR_RGB2Lab);
        List<Mat> channels = new ArrayList<>();
        Core.split(lab, channels);
        Core.MinMaxLocResult resultA = Core.minMaxLoc(channels.get(1));
        Core.MinMaxLocResult resultB = Core.minMaxLoc(channels.get(2));
        double aChrMax = resultA.maxVal - 128;
        double bChrMax = resultB.maxVal - 128;
        double aChrMin = resultA.minVal -128;
        double bChrMin = resultB.minVal -128;
        double upperChroma = Math.sqrt(aChrMax*aChrMax + bChrMax*bChrMax);
        double lowerChroma = Math.sqrt((aChrMin*aChrMin + bChrMin*bChrMin));
        double lowerTresh = upperChroma - 0.5*(upperChroma - lowerChroma);
        double upperTresh = 0.3 * upperChroma;
        double avgChroma = (upperChroma+lowerChroma)/2;

        Scalar meanLab = Core.mean(lab);
        double[] meanVal = meanLab.val;
//        for(double d: meanVal)
//        System.out.println("***meanLab: " + d);
        double ma = meanVal[1]-128;
        double mb = meanVal[2] - 128;
        double meanCh = Math.sqrt(ma*ma + mb*mb);

        upperTresh = 1.5*meanCh;
        lowerTresh = 0.5*meanCh;

        Core.sort(channels.get(1), channels.get(1), Core.SORT_EVERY_ROW + Core.SORT_ASCENDING);
        Core.sort(channels.get(2), channels.get(2), Core.SORT_EVERY_ROW + Core.SORT_ASCENDING);
        for(int j=5;j<6;j++)
        {

            for(int i=0;i<lab.cols();i++)
            {
                double[] vals = lab.get(j,i);
                double ach = vals[1]-128;
                double bch = vals[2]-128;
                double ch = Math.sqrt(ach*ach + bch*bch);

                if(ch > lowerTresh && ch < upperTresh)
                {
                    maxX = left + i;
                    System.out.println("***ch is between: " + lowerTresh +
                            " and " + upperTresh + ". chroma: " +
                            ch + ". maxX new: " + maxX);
                }
                else {
                    if(ch<lowerTresh)
                        System.out.println("***ch is lower than: " + lowerTresh + ". chroma: " +
                                ch + ". maxX new: " + maxX);
                    if(ch>upperTresh)
                        System.out.println("***ch is higher than: " + upperTresh + ". chroma: " +
                                ch + ". maxX new: " + maxX);

                }
            }
        }



        sub = rgb.submat(top , top + (maxY - top), left, left + (maxX-left));

        pts.clear();
        pts.add(new Point(left, top));
        pts.add(new Point(maxX, maxY));

        Scalar mean = Core.mean(sub);


        for(double val: mean.val) {

            System.out.println("***Scalar colors: "+  val);
        }

        int color = Color.rgb((int) Math.round(mean.val[0]), (int) Math.round(mean.val[1]), (int) Math.round(mean.val[2]));
        ResultActivity.stripColors.add(new ResultActivity.ColorDetected(color, left));

        return pts;
    }

    public static int getMinX(List<Point> list)
    {
        int min = Integer.MAX_VALUE;
        for(Point p: list){
            if(p.x < min)
                min = (int) Math.round(p.x);
        }

        return min;
    }
    public static int getMaxX(List<Point> list)
    {
        int max = Integer.MIN_VALUE;
        for(Point p: list){
            if(p.x>max)
                max = (int) Math.round(p.x);
        }

        return max;
    }
    public static int getMinY(List<Point> list)
    {
        int min = Integer.MAX_VALUE;
        for(Point p: list){
            if(p.y < min)
                min = (int) Math.round(p.y);
        }

        return min;
    }
    public static int getMaxY(List<Point> list)
    {
        int max = Integer.MIN_VALUE;
        for(Point p: list){
            if(p.y>max)
                max = (int) Math.round(p.y);
        }

        return max;
    }



    public double focusStandardDev(Mat src)
    {
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();

        Core.meanStdDev(src, mean, stddev);

        Scalar mu = new Scalar(0);
        Scalar sigma = new Scalar(0);

        double focusMeasure = 0;

        for(int i=0;i<mean.rows();i++) {
            for (int j = 0; j < mean.cols(); j++) {
                double[] d = mean.get(i, j);
                if (d[0] > 0 ) {
                    mu = new Scalar(d);
                }
                System.out.println("***mu: " + mu.toString());

            }
        }

        for(int i=0;i<stddev.rows();i++)
        {
            for(int j=0; j< stddev.cols();j++)
            {
                double[] d = stddev.get(i,j);
                if(d[0] > 0) {
                    sigma = new Scalar(d);
                }
                System.out.println("***sigma: " + sigma.toString());

            }
        }

        focusMeasure = (sigma.val[0]*sigma.val[0]) / mu.val[0];


        return focusMeasure;
    }

    public double focusLaplacian(Mat src) {

        int kernel_size = 3;
        int scale = 1;
        int delta = 0;
        int ddepth = CvType.CV_8UC1;
        double maxLap = -32767;

        Mat src_gray = new Mat();
        Mat dst = new Mat();

        Imgproc.GaussianBlur(src, src, new Size(3, 3), 0, 0, Imgproc.BORDER_DEFAULT);
        Imgproc.cvtColor(src, src_gray, Imgproc.COLOR_BGR2GRAY);

        Imgproc.Laplacian(src_gray, dst, ddepth, kernel_size, scale, delta, Imgproc.BORDER_DEFAULT);

        if (!dst.empty()) {

            for (int i = 0; i < dst.rows(); i++) {

                for (int j = 0; j < dst.cols(); j++) {

                    double[] pixelData = dst.get(i, j);
                    if (pixelData != null && pixelData.length > 0) {
                        if (pixelData[0] > maxLap)
                            maxLap = pixelData[0];
                    }

                }
            }
            System.out.println("***maxLap: " + maxLap);

        }
        return maxLap;
    }

    public Mat shadowDetection(Mat src)
    {
        Mat fgmask = new Mat();
        BackgroundSubtractorMOG backgroundSubtractor = new BackgroundSubtractorMOG();
        backgroundSubtractor.apply(src, fgmask);

        return fgmask;
    }

    public Mat shadowDetectionMOG2(Mat src)
    {
        Mat fgmask = new Mat();
        BackgroundSubtractorMOG2 backgroundSubtractorMOG2 = new BackgroundSubtractorMOG2(5, 0.5f, true);

        backgroundSubtractorMOG2.apply(src, fgmask, 0);

        return fgmask;
    }

    //enhance contrast
    public static Mat enhanceContrast(Mat src) {
        Mat equalsrc = new Mat();
        Mat dest = new Mat();
        List<Mat> channels = new ArrayList<>();
        Imgproc.cvtColor(src, equalsrc, Imgproc.COLOR_RGB2YCrCb);
        Core.split(equalsrc, channels);
        Imgproc.equalizeHist(channels.get(0), channels.get(0));
        Core.merge(channels, equalsrc);
        Imgproc.cvtColor(equalsrc, dest, Imgproc.COLOR_YCrCb2RGB);

        return dest;
    }

    //sharpen image
    public static Mat sharpen(Mat src) {
        Mat dest = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.CV_SHAPE_RECT, new Size(3, 3));
        Imgproc.filter2D(src, dest, -1, kernel);
        Imgproc.GaussianBlur(dest, dest, new Size(3, 3), 0, 0);
        Core.addWeighted(src, 1.5, dest, -0.5, 0, dest);

        return dest;
    }

    public static class PointComparator implements Comparator<Point>
    {

        @Override
        public int compare(Point lhs, Point rhs) {

            if(lhs.x + lhs.y < rhs.x + rhs.y)
            {
                return -1;
            }

            else
            {
                return 1;
            }

        }
    }
}
