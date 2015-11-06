package com.rinke.solutions.pinball.model;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

public class Project implements Model {
	public byte version;
	public String inputFile;
	public List<Palette> palettes;
	public List<PalMapping> palMappings;
	public Project(int version, String inputFile, List<Palette> palettes,
			List<PalMapping> palMappings) {
		super();
		this.version = (byte)version;
		this.inputFile = inputFile;
		this.palettes = palettes;
		this.palMappings = palMappings;
	}
	@Override
	public String toString() {
		return "Project [version=" + version + ", inputFile=" + inputFile
				+ ", palettes=" + palettes + ", palMappings=" + palMappings
				+ "]";
	}
	
	public void writeTo(DataOutputStream os) throws IOException {
		os.writeByte(version);
		os.writeUTF(inputFile);
		os.writeShort(palettes.size());
		for(Palette p: palettes) {
			p.writeTo(os);
		}
		os.writeShort(palMappings.size());
		for(PalMapping p : palMappings ) {
			p.writeTo(os);
		}
	}
}
