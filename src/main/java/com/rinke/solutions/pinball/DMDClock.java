package com.rinke.solutions.pinball;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.rinke.solutions.pinball.renderer.FrameSet;
import com.rinke.solutions.pinball.renderer.PngRenderer;
import com.rinke.solutions.pinball.renderer.Renderer;

public class DMDClock {
	
	Renderer renderer = new PngRenderer();

	Map<Character,DMD> charMapBig = new HashMap<Character, DMD>();
	Map<Character,DMD> charMapSmall = new HashMap<Character, DMD>();
	Map<Character,DMD> charMapBigMask = new HashMap<Character, DMD>();
	Map<Character,DMD> charMapSmallMask = new HashMap<Character, DMD>();

	DMD emptyBig = new DMD(8, 32);
	DMD emptySmall = new DMD(8, 9);
	
	boolean fadeIn = true;
	int renderCycles = 0;
	
	boolean showSeconds = false;

	public void restart() {
		renderCycles = 0;
	}
	
	String alpha = "0123456789: .C*";
	
	public DMDClock(boolean showSeconds) {
		super();
		this.showSeconds = showSeconds;
		
		// check for compiled font first
		if( true /*!loadFontData("font.dat") */) {

			String base = "/home/sr/Downloads/Pinball/";
			int i = 0;
			// 352 - 35c fuer klein 6x9 
			// alter big font 0x352; j <= 0x035C
			for (int j = 0x013; j <= 0x021; j++) {
				DMD dmd = new DMD(8, 9);
				FrameSet frameSet = renderer.convert(base + "small", dmd,j);
				dmd.writeOr(frameSet);
				charMapSmall.put(alpha.charAt(i), dmd);
				i++;
			}
			i = 0;
			for (int j = 0x013; j <= 0x021; j++) {
				DMD dmd = new DMD(8, 9);
				((PngRenderer) renderer).setPattern("Image-0x%04X-mask");
				FrameSet frameSet = renderer.convert(base + "small", dmd,j);
				dmd.writeOr(frameSet);
				charMapSmallMask.put(alpha.charAt(i), dmd);
				i++;
			}
			i = 0;
			for (int j = 0x16A; j <= 0x0178; j++) {
				DMD dmd = new DMD(j>=0x174&&j<=0x176?8:16, 32);
				((PngRenderer) renderer).setPattern("Image-0x%04X");
				FrameSet frameSet = renderer.convert(base + "big", dmd,j);
				dmd.writeOr(frameSet);
				charMapBig.put(alpha.charAt(i), dmd);
				i++;
			}
			i = 0;
			for (int j = 0x16A; j <= 0x0178; j++) {
				DMD dmd = new DMD(j>=0x174&&j<=0x176?8:16, 32);
				((PngRenderer) renderer).setPattern("Image-0x%04X-mask");
				FrameSet frameSet = renderer.convert(base + "big", dmd,j);
				dmd.writeOr(frameSet);
				charMapBigMask.put(alpha.charAt(i), dmd);
				i++;
			}

			writeFontData("font.dat");
		}
	}
	
	private boolean loadFontData(String filename) {
//		File file = new File(filename);
//		if( !file.exists() ) return false;
		DataInputStream is = null;
		try{
			InputStream stream = this.getClass().getResourceAsStream("/"+filename);
			is = new DataInputStream(stream);
			readMap(charMapBig,is);
			readMap(charMapBigMask,is);
			readMap(charMapSmall,is);
			readMap(charMapSmallMask,is);
		} catch(IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				if( is != null ) is.close();
			} catch (IOException e) {
			}
		}
		return true;
	}

	private void readMap(Map<Character, DMD> charMap, DataInputStream is) throws IOException {
		int size = is.readByte();
		charMap.clear();
		while(size-- > 0) {
			char c = (char) is.readByte();
			int w = is.readByte();
			int h = is.readByte();
			is.readShort();
			DMD dmd = new DMD(w, h);
			is.read(dmd.frame1);
			for( int i = 0; i < dmd.frame1.length; i++) {
				dmd.frame1[i] ^= 0x00;
			}
			charMap.put(c, dmd);
		}
	}

	private void writeFontData(String filename) {
		DataOutputStream os = null;
		try {
			os = new DataOutputStream(new FileOutputStream(filename));
			writeMap(charMapBig,os);
			writeMap(charMapBigMask,os);
			writeMap(charMapSmall,os);
			writeMap(charMapSmallMask,os);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if(os != null ) os.close();
			} catch (IOException e) {
			}
		}
	}

	private void writeMap(Map<Character, DMD> charMap, DataOutputStream os) throws IOException {
		os.writeByte(charMap.size());
		for(int i = 0; i < charMap.size();i++) {
			char c = alpha.charAt(i);
			os.writeByte(c);
			DMD dmd = charMap.get(Character.valueOf(c));//.writeTo(os);
			os.writeByte(dmd.getWidth());
			os.writeByte(dmd.getHeight());
			os.writeShort(dmd.getFrameSizeInByte());
			//os.write(dmd.transformFrame1(dmd.frame1));
			os.write(dmd.frame1);
		}
	}

	public void renderTime(DMD dmd) {
		renderTime(dmd,false,showSeconds?0:24,0);
	}
	
	public void renderTime(DMD dmd, boolean small, int x, int y) {
		int xoffset = x/8; // only byte aligned
		
		String time = String.format("%tT", Calendar.getInstance());
		if( !showSeconds ) {
			time = time.substring(0,5);
		}
		Map<Character,DMD> map = small?charMapSmall:charMapBig;
		
		for(int i = 0; i<time.length(); i++) {
			DMD src = map.get(time.charAt(i));
			if( ':' == time.charAt(i) && (System.currentTimeMillis() % 1000) < 500 ) {
				src = small?emptySmall:emptyBig;
			}
			if( src != null ) {
				boolean low = renderCycles < 1;
				copy(dmd, y, xoffset, small?emptySmall:emptyBig, low, small);
				copy(dmd, y, xoffset, src, low, small);
				xoffset += src.getWidth()/8;
			}
		}
		renderCycles++;
	}

	// TODO methode in DMD selbst
	private void copy(DMD target, int yoffset, int xoffset, DMD src, boolean low, boolean small) {
		if( small ){
			for( int row = 0; row <9; row++) {
				target.frame1[(row+yoffset)*target.getBytesPerRow()+xoffset] = src.frame1[src.getBytesPerRow()*row];
				if(!low) {
					target.frame2[(row+yoffset)*target.getBytesPerRow()+xoffset] = src.frame1[src.getBytesPerRow()*row];
				}
			}
		} else {
			for( int row = 0; row <=27; row++) {
				target.frame1[(row+yoffset)*target.getBytesPerRow()+xoffset] = src.frame1[src.getBytesPerRow()*row];
				if( src.getWidth()==16) target.frame1[(row+yoffset)*target.getBytesPerRow()+xoffset+1] = src.frame1[src.getBytesPerRow()*row+1];
				if(!low) {
					target.frame2[(row+yoffset)*target.getBytesPerRow()+xoffset] = src.frame1[src.getBytesPerRow()*row];
					if( src.getWidth()==16) target.frame2[(row+yoffset)*target.getBytesPerRow()+xoffset+1] = src.frame1[src.getBytesPerRow()*row+1];
				}
			}
		}
	}
	
	private String dumpAsCode() {
		StringBuilder sb = new StringBuilder();
		for(Entry<Character, DMD> c: charMapBig.entrySet() ) {
			sb.append("// big " +c.getKey()+"\n");
			sb.append(c.getValue().dumpAsCode());
		}
//		for(Entry<Character, DMD> c: charMapSmall.entrySet() ) {
//			sb.append("// small " +c.getKey()+"\n");
//			sb.append(c.getValue().dumpAsCode(5));
//		}
		return sb.toString();
	}
	
	public static void main( String[] args ) {
		DMDClock clock = new DMDClock(false);
		System.out.println(clock.dumpAsCode());
	}

}
