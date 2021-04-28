package com.rinke.solutions.pinball.animation;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import com.rinke.solutions.pinball.model.Frame;
import com.rinke.solutions.pinball.model.Palette;
import com.rinke.solutions.pinball.model.RGB;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnimationInterpolator {
	
	private Map<Frame,Mat> mat2frame = new HashMap<>();
	private Palette pal;
	private int h;
	private int w;

	public AnimationInterpolator(CompiledAnimation src, Palette pal) {
		this.w = src.width;
		this.h = src.height;
		this.pal = pal;
	}

	/**
	 * check if scene can interpolated: there must be at least one keyframe
	 * 
	 * @param src
	 *            ani to check
	 * @return null for okay, error message if not
	 */
	public String validate(CompiledAnimation src) {
		int keyFrames = 0;
		for (Frame frame : src.frames) {
			if (frame.keyFrame)
				keyFrames++;
		}
		if (keyFrames <= 1)
			return "Scene has no (or only one) keyframes";
		return null;
	}

	// see https://stackoverflow.com/questions/14958643/converting-bufferedimage-to-mat-in-opencv
	public static Mat bufferedImageToMat(BufferedImage bi) {
		Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);
		byte[] data = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
		mat.put(0, 0, data);
		return mat;
	}
	
	private Mat getMatfromFrame(Frame f) {
		Mat m = mat2frame.get(f);
		if( m != null ) return m;
		byte[] data = bgrImageDatafromFrame(f,this.w, this.h, this.pal);
		m = new Mat(this.h, this.w, CvType.CV_8UC3);
		m.put(0, 0, data);
//		log.debug("write {}",f.hashCode());
//		Imgcodecs.imwrite("img"+f.hashCode()+".png", m);
		mat2frame.put(f, m);
		return m;
	}
	
	public static byte[] bgrImageDatafromFrame(Frame f, int w, int h, Palette p) {
		byte[] bi = new byte[ w*h*3 ]; // use BGR color model as expected by opencv
		int numberOfSubframes = f.planes.size();
		log.debug("creating buffered image w={}, h={}, subframes={}, p={}", w, h, numberOfSubframes, p.name);
		// int bitsPerColorChannel = numberOfSubframes / 3;
		//int cmask = 0xFF >> (8-bitsPerColorChannel);
		int bytesPerRow = w / 8; // only work with byte 8-aligned width
		for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // lsb first
                // byte mask = (byte) (1 << (col % 8));
                // hsb first
                byte mask = (byte) (0b10000000 >> (x % 8));
                int v = 0;
                for(int i = 0; i < numberOfSubframes;i++) {
                	if( x / 8 + y * bytesPerRow < f.getPlane(i).length) {
                		v += (f.getPlane(i)[x / 8 + y * bytesPerRow] & mask) != 0 ? (1<<i) : 0;
                	}
                }
                // TODO add sanity check that v is in range
                RGB rgb = p.colors[v];
                //bi.setRGB(x, y, rgb.red << 16 + rgb.green << 8 + rgb.blue );
                int j = w*y*3 + x*3;
                bi[j] = (byte)rgb.blue;
                bi[j+1] = (byte)rgb.green;
                bi[j+2] = (byte)rgb.red;
            }
        }
		return bi;
	}

	public CompiledAnimation interpolate(String name, CompiledAnimation src, Palette pal) {
		//  copy src
		CompiledAnimation res = src.cutScene(src.start, src.end, src.getNumberOfPlanes());
		for (int i = 0; i < res.frames.size(); i++) {
			if (res.frames.get(i).keyFrame) {
				for (int j = i + 1; j < res.frames.size(); j++) {
					if (res.frames.get(j).keyFrame) {
						this.interpolateFrames(res.frames, i, j, pal, res.width, res.height);
						i = j-1;
						break;
					}
				}
			}
		}
		return res;
	}

	private List<Frame> interpolateFrames(List<Frame> frames, int startKeyFrame, int endKeyFrame, Palette pal, int w, int h) {
		int nextLowToInterpolate = startKeyFrame + 1;
		int nextHighToInterpolate = endKeyFrame - 1;
		log.debug("startKeyFrame={}, endKeyFrame={}", startKeyFrame, endKeyFrame);
		List<Frame> result = new ArrayList<>();
		while (nextHighToInterpolate - nextLowToInterpolate >= 0) {
			double histHighDiff = this.getBestNeighborhoodKeyframe(frames.get(nextHighToInterpolate), frames.get(nextHighToInterpolate + 1), pal);
			double histLowDiff = this.getBestNeighborhoodKeyframe(frames.get(nextLowToInterpolate - 1), frames.get(nextLowToInterpolate), pal);
			log.debug("nextHighToInterpolate={}, nextLowToInterpolate={}, histHighDiff={}, histLowDiff={}", nextHighToInterpolate, nextLowToInterpolate,
					histHighDiff, histLowDiff);
			if (histHighDiff < histLowDiff) {
				Frame f = this.propagateFrameFromTo(frames.get(nextHighToInterpolate + 1), frames.get(nextHighToInterpolate), pal);
				frames.set(nextHighToInterpolate, f);
				nextHighToInterpolate--;
			} else {
				Frame f = this.propagateFrameFromTo(frames.get(nextLowToInterpolate - 1), frames.get(nextLowToInterpolate), pal);
				frames.set(nextLowToInterpolate, f);
				nextLowToInterpolate++;
			}
		}
		releaseCachedFrames();
		return result;
	}

	private void releaseCachedFrames() {
		for( Mat m : this.mat2frame.values() ) m.release();
		this.mat2frame.clear();
	}

	public BufferedImage toBufferedImage(Mat matrix) {
		int type = BufferedImage.TYPE_BYTE_GRAY;
		if (matrix.channels() > 1) {
			type = BufferedImage.TYPE_3BYTE_BGR;
		}
		int bufferSize = matrix.channels() * matrix.cols() * matrix.rows();
		byte[] buffer = new byte[bufferSize];
		matrix.get(0, 0, buffer); // get all the pixels
		BufferedImage image = new BufferedImage(matrix.cols(), matrix.rows(), type);
		final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		System.arraycopy(buffer, 0, targetPixels, 0, buffer.length);
		return image;
	}

	private Frame propagateFrameFromTo(Frame srcframe, Frame frameToInterpolate, Palette pal) {
		// calc motion flow
		Mat msrc = getMatfromFrame(srcframe);
		Mat minter = getMatfromFrame(frameToInterpolate);
        Mat flow = new Mat(msrc.size(), CvType.CV_32FC2);
        Mat minter_gray = new Mat(minter.size(), CvType.CV_8UC1);
        Mat msrc_gray = new Mat(msrc.size(), CvType.CV_8UC1);
        Imgproc.cvtColor(minter, minter_gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(msrc, msrc_gray, Imgproc.COLOR_BGR2GRAY);
        Video.calcOpticalFlowFarneback(msrc_gray, minter_gray, flow, 0.5, 3, 15, 3, 7, 1.5, 0);
        Frame res = new Frame(frameToInterpolate);
        for(int x = 0; x < this.w; x++) {
        	for( int y = 0; y < this.h; y++) {
        		//log.debug("x={}, y={}", x, y);
        		double[] d = flow.get(y,x);
                int dx = (int) Math.round(d[0]);
                int dy = (int) Math.round(d[1]);
                int ppx = x - dx;
                int ppy = y - dy;
                if( ppx < 0)  //  # curate edges of frame
                	ppx = 0;
                if( ppx > this.w - 1)
                	ppx = this.w - 1;
                if( ppy < 0)
                	ppy = 0;
                if( ppy > this.h - 1)
                	ppy = this.h - 1;
                int colIdx = getPixel(srcframe, ppx, ppy, this.w/8);
//                if( colIdx > 0 ) {
//                	log.debug("x={}, y={}, dx={}, dy={} d[0]={} d[1]={}", x,y,dx, dy, d[0], d[1]);
//                }
                setPixel(res, colIdx, x, y, this.w/8);
        	}
        }
        msrc_gray.release();
        minter_gray.release();
        flow.release();
		return res;
	}
	
	private void setPixel(Frame r, int v, int x, int y, int bytesPerRow) {
    	byte mask = (byte) (0b10000000 >> (x % 8));
    	int numberOfPlanes = r.planes.size();
    	for(int plane = 0; plane < numberOfPlanes; plane++) {
    		if( (v & 0x01) != 0) {
    			r.planes.get(plane).data[y*bytesPerRow+x/8] |= mask;
    		} else {
    			r.planes.get(plane).data[y*bytesPerRow+x/8] &= ~mask;
    		}
    		v >>= 1;
    	}
	}
	
	private int getPixel(Frame frame, int x, int y, int bytesPerRow) {
    	byte mask = (byte) (0b10000000 >> (x % 8));
    	int v = 0;
    	for(int plane = 0; plane < frame.planes.size(); plane++) {
    		v += (frame.planes.get(plane).data[x / 8 + y * bytesPerRow] & mask) != 0 ? (1<<plane) : 0;
    	}
    	return v;
	}

	private double getBestNeighborhoodKeyframe(Frame frame1, Frame frame2, Palette pal) {
		// done by histogram_difference_between_frames
		float[] range = { 0, 256 }; // the upper boundary is exclusive
		MatOfFloat histRange = new MatOfFloat(range);
		MatOfInt hist1 = new MatOfInt(), hist2 = new MatOfInt();
		List<Mat> bgrPlanes1 = new ArrayList<>();
		//  add converted frame
		bgrPlanes1.add(getMatfromFrame(frame1));
		// cv2.calcHist((image_from_frame(frame1, palette)), [0], None, [256], [0, 256])
		// calcHist(List<Mat> images, MatOfInt channels, Mat mask, Mat hist, MatOfInt histSize, MatOfFloat ranges)
		Imgproc.calcHist(bgrPlanes1, new MatOfInt(0), new Mat(), hist1, new MatOfInt(256), histRange);
		List<Mat> bgrPlanes2 = new ArrayList<>();
		//  add converted frame2
		bgrPlanes2.add(getMatfromFrame(frame2));
		Imgproc.calcHist(bgrPlanes2, new MatOfInt(0), new Mat(), hist2, new MatOfInt(256), histRange);
		//log.debug("size of hist1/2 = {} {}", hist1.size(), hist2.size());
		//log.debug("hist1={}", hist1.dump());
		//log.debug("hist2={}", hist2.dump());
		return Imgproc.compareHist(hist1, hist2, 3);
	}


}
