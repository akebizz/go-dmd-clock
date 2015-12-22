package com.rinke.solutions.pinball.animation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rinke.solutions.pinball.DMD;
import com.rinke.solutions.pinball.renderer.AnimatedGIFRenderer;
import com.rinke.solutions.pinball.renderer.DMDFRenderer;
import com.rinke.solutions.pinball.renderer.DummyRenderer;
import com.rinke.solutions.pinball.renderer.PcapRenderer;
import com.rinke.solutions.pinball.renderer.PngRenderer;
import com.rinke.solutions.pinball.renderer.Renderer;
import com.rinke.solutions.pinball.renderer.VPinMameRenderer;


public class Animation {
    
    private static Logger LOG = LoggerFactory.getLogger(Animation.class); 

	protected String basePath = "./";
	protected String transitionsPath = "./";
	
	// teil der zum einlesen gebraucht wird
	public int start = 0;
	public int end = 0;
	public  int skip = 2;
	private String pattern = "Image-0x%04X";
	private boolean autoMerge;

	// meta daten
	private int cycles = 1;
	private String name;
	private int holdCycles;
	private AnimationType type;
	private int refreshDelay = 100;
	// defines at which frame clock should reappear
	private int clockFrom;
	// should we use small clock in animation
	private boolean clockSmall = false;
	private int clockXOffset = 24;
	private int clockYOffset = 3;
	private boolean clockInFront = false;
	private int fsk = 16;
	
	private int transitionFrom = 0;
	private String transitionName = null;
	private int transitionCount = 1;
	private int transitionDelay = 50;
	private boolean loadedFromFile = false;
	
	public int getFsk() {
		return fsk;
	}

	public void setFsk(int fsk) {
		this.fsk = fsk;
	}

	public boolean isClockInFront() {
		return clockInFront;
	}

	public void setClockInFront(boolean clockInFront) {
		this.clockInFront = clockInFront;
	}

	// runtime daten
	public int actFrame;
	boolean ended = false;
	private int actCycle;
	int holdCount = 0;
	
	private String desc;
	
	public Animation cutScene( int start, int end, int actualNumberOfPlanes) {
		// create a copy of the animation
		DMD tmp = new DMD(128,32);
		CompiledAnimation dest = new CompiledAnimation(
				AnimationType.COMPILED, this.getName(),
				0, end-start, this.skip, 1, 1);
		dest.setLoadedFromFile(false);
		// rerender and thereby copy all frames
		this.actFrame = start;
		for (int i = start; i <= end; i++) {
			Frame frame = this.render(tmp, false);
			while( frame.planes.size() < actualNumberOfPlanes ) {
				frame.planes.add(new Plane(frame.planes.get(0).marker, frame.planes.get(0).plane));
			}
			dest.frames.add(new Frame(frame));
		}
		return dest;
	}

    public static Animation buildAnimationFromFile(String filename, AnimationType type) {
        File file = new File(filename);
        if( !file.canRead() ) {
            throw new RuntimeException("Could not read '"+filename+"' to load animation");
        }
        String base = file.getName();
        Animation ani = new Animation(type, base, 0, 0, 1, 1, 0);
        ani.setBasePath(file.getParent() + "/");
        ani.setDesc(base.substring(0, base.indexOf('.')));
        ani.setLoadedFromFile(true);
        return ani;
    }

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public int getFrameCount(DMD dmd) {
		int r =  ((end-start)/skip)+1;
		// make use of transition length
		if( transitionFrom>0) {
			initTransition(dmd);
			r += transitions.size()-1;
			r -= (end-transitionFrom)/skip;
		}
		return r;
	}
	
	public int getCycles() {
		return cycles;
	}

	public void setCycles(int cycles) {
		this.cycles = cycles;
	}

	public int getHoldCycles() {
		return holdCycles;
	}

	public void setHoldCycles(int holdCycles) {
		this.holdCycles = holdCycles;
	}

	public AnimationType getType() {
		return type;
	}

	public void setType(AnimationType type) {
		this.type = type;
	}

	public int getClockFrom() {
		return clockFrom;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setClockFrom(int clockFrom) {
		this.clockFrom = clockFrom;
	}

	public String getName() {
		return name;
	}

	public void setPattern(String pattern) {
		this.pattern = pattern;
	}

	public int getRefreshDelay() {
		if( last != null && last.delay > 0 ) return last.delay;
		return refreshDelay;
	}

	public void setRefreshDelay(int refreshDelay) {
		this.refreshDelay = refreshDelay;
	}

	public Animation(AnimationType type, String name, int start, int end, int skip,
			int cycles, int holdCycles) {
		super();
		this.start = start;
		this.actFrame = start;
		this.end = end;
		this.skip = skip;
		this.cycles = cycles;
		actCycle = 0;
		this.name = name;
		this.holdCycles = holdCycles;
		this.type = type;
		this.clockFrom = 20000;
	}

	Renderer renderer = null;
	Frame last;
	Renderer transitionRenderer = null;
	List<Frame> transitions = new ArrayList<>();
	
	protected Frame renderFrame(String name, DMD dmd, int act) {
		return renderer.convert(name, dmd, act);
	}
	
	public Renderer getRenderer() {
		if( renderer == null ) init();
		return renderer;
	}
	
	public long getTimeCode(int actFrame) {
	    return renderer.getTimeCode(actFrame);
	}
	
	public Frame render(DMD dmd, boolean stop) {
		
		if( transitionName != null && transitions.isEmpty() ) {
			initTransition(dmd);
		}
		
		Frame frame = null;
		if( renderer == null ) init();
		
		int maxFrame = renderer.getMaxFrame(basePath+name, dmd);
		if(  maxFrame > 0 && end == 0) end = maxFrame-1;
		if (actFrame <= end) {
			ended = false;
			last = renderFrame(basePath+name, dmd, actFrame);
			if( !stop) actFrame += skip;
		} else if (++actCycle < cycles) {
			actFrame = start;
		} else {
			if (holdCount++ >= holdCycles && transitionCount>=transitions.size()) {
			    ended = true;
			}
			actCycle = 0;
		}
		frame = last;
		if( transitionFrom != 0 // it has a transition
		    && actFrame > transitionFrom  // it has started
			&& transitionCount<=transitions.size() // and not yet ended
				) {
		    frame = addTransitionFrame(frame);
			transitionCount++;
		}
		return frame;
	}
	
    protected Frame addTransitionFrame(Frame in) {
        Frame tframe = transitions.get(transitionCount < transitions.size() ? transitionCount : transitions.size() - 1);
        Frame r = new Frame(in.width, in.height, in.planes.get(0).plane, in.planes.get(1).plane);//
        r.delay = in.delay;
        r.planes.add(tframe.planes.get(0));
        return r;
    }

    public void initTransition(DMD dmd) {
	    LOG.debug("init transition: "+transitionName);
	    transitions.clear();
	    transitionCount=1;
		while(true) {
			Frame frame;
			try {
				frame = transitionRenderer.convert(transitionsPath+"transitions", dmd, transitionCount++);
				frame.planes.forEach( p -> p.marker = 0x6a);
				transitions.add(frame);
			} catch( RuntimeException e) {
			    LOG.info(e.getMessage());
				break;
			}
		}
		transitionCount=0;
	}

	public boolean addClock() {
		return actFrame>clockFrom | (transitionFrom>0 && actFrame>=transitionFrom);
	}

	protected void init() {
		switch (type) {
		case PNG:
			renderer = new PngRenderer(pattern,autoMerge);
			break;
		case DMDF:
			renderer = new DMDFRenderer();
			break;
		case GIF:
			renderer = new AnimatedGIFRenderer();
			break;
		case MAME:
			renderer = new VPinMameRenderer();
			break;
		case PCAP:
			renderer = new PcapRenderer();
			break;
		case COMPILED:
			renderer = new DummyRenderer();
			break;
		default:
			break;
		}
	}

	public boolean hasEnded() {
		return ended;
	}

	public void restart() {
		ended = false;
		actCycle = 0;
		actFrame = start;
		holdCount = 0;
		transitionCount=0;
	}

	public void setAutoMerge(boolean b) {
		this.autoMerge = b;
	}

	public void setClockSmall(boolean clockSmall) {
		this.clockSmall = clockSmall;
	}

	public void setClockXOffset(int clockXOffset) {
		this.clockXOffset = clockXOffset;
	}

	public void setClockYOffset(int clockYOffset) {
		this.clockYOffset = clockYOffset;
	}

	public boolean isClockSmall() {
		return clockSmall;
	}

	public int getClockXOffset() {
		return clockXOffset;
	}

	public int getClockYOffset() {
		return clockYOffset;
	}

	public int getStart() {
		return start;
	}

	public void setStart(int start) {
		this.start = start;
	}

	public void next() {
		if (actFrame <= end) {
			actFrame += skip;
		}
	}

	public void prev() {
		if (actFrame > start) {
			actFrame -= skip;
		}
	}

	public void setPos(int pos) {
		if( pos >= start && pos <= end ) {
			actFrame = pos;
		}
	}

	public int getTransitionFrom() {
		return transitionFrom;
	}

	public void setTransitionFrom(int transitionFrom) {
		this.transitionFrom = transitionFrom;
	}

	public String getTransitionName() {
		return transitionName;
	}

	public void setTransitionName(String transitionName) {
		this.transitionName = transitionName;
		this.transitionRenderer = new PngRenderer(transitionName+"%d",false);
		transitions.clear();
	}

	public int getTransitionDelay() {
		return transitionDelay;
	}

	public void setTransitionDelay(int transitionDelay) {
		this.transitionDelay = transitionDelay;
	}

	public String getBasePath() {
		return basePath;
	}

	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}

	public int getActFrame() {
		return actFrame;
	}

	public void setActFrame(int actFrame) {
		this.actFrame = actFrame;
	}

    public String getTransitionsPath() {
        return transitionsPath;
    }

    public void setTransitionsPath(String transitionsPath) {
        this.transitionsPath = transitionsPath;
    }

    @Override
    public String toString() {
        return "Animation [start=" + start + ", end=" + end + ", skip=" + skip + ", cycles=" + cycles + ", name=" + name
                + ", holdCycles=" + holdCycles + ", type=" + type + ", refreshDelay=" + refreshDelay + ", clockFrom="
                + clockFrom + ", clockSmall=" + clockSmall + ", clockXOffset=" + clockXOffset + ", clockYOffset="
                + clockYOffset + ", clockInFront=" + clockInFront + ", fsk=" + fsk + ", transitionFrom=" + transitionFrom
                + ", transitionName=" + transitionName + ", transitionDelay=" + transitionDelay + ", desc=" + desc + "]";
    }

    public void setPixel( int x, int y) {
    }

	public void commitDMDchanges(DMD dmd) {
	}

	public boolean isLoadedFromFile() {
		return loadedFromFile;
	}

	public void setLoadedFromFile(boolean loadedFromFile) {
		this.loadedFromFile = loadedFromFile;
	}

}
