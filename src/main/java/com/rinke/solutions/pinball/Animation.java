package com.rinke.solutions.pinball;

import com.rinke.solutions.pinball.renderer.AnimatedGIFRenderer;
import com.rinke.solutions.pinball.renderer.DMDFRenderer;
import com.rinke.solutions.pinball.renderer.FrameSet;
import com.rinke.solutions.pinball.renderer.PngRenderer;
import com.rinke.solutions.pinball.renderer.Renderer;


public class Animation {

	// teil der zum einlesen gebraucht wird
	protected int start = 0;
	protected int end = 0;
	private int skip = 2;
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

	// runtime daten
	int act;
	boolean ended = false;
	private int actCycle;
	int holdCount = 0;

	public int getFrameSetCount() {
		return (end+1-start)/skip;
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
		return refreshDelay;
	}

	public void setRefreshDelay(int refreshDelay) {
		this.refreshDelay = refreshDelay;
	}

	public Animation(AnimationType type, String name, int start, int end, int skip,
			int cycles, int holdCycles) {
		super();
		this.start = start;
		this.act = start;
		this.end = end;
		this.skip = skip;
		this.cycles = cycles;
		actCycle = 0;
		this.name = name;
		this.holdCycles = holdCycles;
		this.type = type;
		this.clockFrom = 20000;
	}

	String basePath = "/home/sr/Downloads/Pinball/";
	Renderer r = null;
	FrameSet last;
	
	protected FrameSet renderFrameSet(String name,DMD dmd, int act) {
		return r.convert(name, dmd, act);
	}
	
	public Renderer getRenderer() {
		if( r == null ) init();
		return r;
	}
	
	public FrameSet render(DMD dmd) {
		if( r == null ) init();
		if (act <= end) {
			ended = false;
			last = renderFrameSet(basePath+name, dmd, act);
			act += skip;
			return last;
		} else if (++actCycle < cycles) {
			act = start;
		} else {
			if (holdCount++ >= holdCycles)
				ended = true;
			actCycle = 0;
		}
		return last;
	}
	
	public boolean addClock() {
		return act>clockFrom;
	}

	private void init() {
		switch (type) {
		case PNG_SEQ:
			r = new PngRenderer(pattern,autoMerge);
			break;
		case DMDF:
			r = new DMDFRenderer();
			break;
		case GIF:
			r = new AnimatedGIFRenderer();
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
		act = start;
		holdCount = 0;
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

}
