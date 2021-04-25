package com.rinke.solutions.pinball.renderer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.zip.GZIPInputStream;

import lombok.extern.slf4j.Slf4j;

import com.rinke.solutions.pinball.DMD;
import com.rinke.solutions.pinball.model.Frame;
import com.rinke.solutions.pinball.model.Plane;

// als parameter in der Steuerdatei sollten
// die helligkeits schwellen angebbar sein
@Slf4j
public class VPinMameRenderer extends Renderer {

	@Override
	public long getTimeCode(int actFrame) {
		return actFrame < frames.size() ? frames.get(actFrame).timecode : 0;
	}

	void readImage(String filename, DMD dmd) {
		BufferedReader stream = null;
		int frameNo = 0;
		int timecode = 0;
		long lastTimeStamp = 0;
		try {
			stream = getReader(filename);
			String line = stream.readLine();
			Frame res = new Frame(
					new byte[dmd.getPlaneSize()],
					new byte[dmd.getPlaneSize()],
					new byte[dmd.getPlaneSize()],
					new byte[dmd.getPlaneSize()]);

			int j = 0;
			int vmax = 0;
			//Map<Integer,Integer> count1 = new HashMap<>();
			//Map<Integer,Integer> count2 = new HashMap<>();
			while (line != null) {
				if (line.startsWith("0x")) {
					long newTs = Long.parseLong(line.substring(2), 16);	
					if (frameNo > 0 && lastTimeStamp > 0) {
						//System.out.println(newTs+":"+(newTs - lastTimeStamp));
						frames.get(frameNo - 1).delay = (int) (newTs - lastTimeStamp);
						timecode += (newTs - lastTimeStamp);
						res.timecode = timecode;
					}
					lastTimeStamp = newTs;
					line = stream.readLine();
					continue;
				}
				int lineLenght = line.length();
				if (lineLenght == 0) {
					// check for number of rows
					int noOfRows = j / dmd.getBytesPerRow();
					if( noOfRows == 16 && dmd.getHeight() == 32 ) {
						res = centerRows( res, dmd.getPlaneSize(), dmd.getBytesPerRow() );
					}
					frames.add(res);
					frameNo++;
					notify(50, "reading "+bareName(filename)+"@"+frameNo);
					res = new Frame(
							new byte[dmd.getPlaneSize()],
							new byte[dmd.getPlaneSize()],
							new byte[dmd.getPlaneSize()],
							new byte[dmd.getPlaneSize()]
							);
					log.trace("reading frame: " + frameNo);
					j = 0;
					line = stream.readLine();
					continue;
				}
				int charsToRead = Math.min(dmd.getWidth(), line.length());
				if( dmd.getWidth() != line.length() ) {
					// TODO SR for different sources this could be normal
					//log.warn("unexpected line length={}, line: {}", line.length(), line);
				}
				for (int i = 0; i<charsToRead; i++) {
					int k = i;
					if( lineLenght > dmd.getWidth()){
						//v1 = Integer.parseInt(line.substring(i,i+1), 16);
						//inc(count1,v1);
						i++; // skip every other byte
						k >>= 1;
					}
					int bit = (k % 8);
					int b = (k >> 3);
					int mask = (0b10000000 >> bit);
					int v = hex2int(line.charAt(i));
//					inc(count2,v);
					if( v > vmax ) vmax = v;
					if( (v & 1) != 0 ) 
						res.planes.get(0).data[j + b] |= mask;
					if( (v & 2) != 0 )
						res.planes.get(1).data[j + b] |= mask;
					if( (v & 4) != 0 )
						res.planes.get(2).data[j + b] |= mask;
					if( (v & 8) != 0 )
						res.planes.get(3).data[j + b] |= mask;
				}
				j += dmd.getBytesPerRow();
				line = stream.readLine();
			}
			// check maximum value for v
			// if never ever more than 3 reduce number of planes
			if( vmax <= 3 ) reducePlanes(frames,2);
		} catch (IOException e) {
			throw new RuntimeException("error reading", e);
		} finally {
			if (stream != null)
				try {
					stream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		this.maxFrame = frameNo;
	}

	private Frame centerRows(Frame in, int planeSize, int bytesPerRow) {
		Frame out = new Frame(
				new byte[planeSize],
				new byte[planeSize],
				new byte[planeSize],
				new byte[planeSize]
				);
		for(int p = 0; p<in.planes.size(); p++) {
			for( int row = 8; row < 24; row++) {
				System.arraycopy(in.planes.get(p).data, (row-8)*bytesPerRow, out.planes.get(p).data, row*bytesPerRow, bytesPerRow);
			}
		}
		return out;
	}

	int hex2int(char ch) {
		if( ch >= '0' && ch <= '9') return ch - '0';
		if( ch >= 'A' && ch <= 'F') return ch -'A' + 10;
		if( ch >= 'a' && ch <= 'f') return ch -'a' + 10;
		return 0;
	}

	/*private void inc(Map<Integer, Integer> map, int v) {
		if( map.containsKey(v)) {
			map.put(v, map.get(v)+1);
		} else {
			map.put(v, 1);
		}
		
	}*/

	private void reducePlanes(List<Frame> frames, int maxNumberOfPlanes) {
		for (Frame frame : frames) {
			List<Plane> planes = frame.planes;
			while( planes.size() > maxNumberOfPlanes ) {
				planes.remove(planes.size()-1);
			}
		}
		
	}

}
