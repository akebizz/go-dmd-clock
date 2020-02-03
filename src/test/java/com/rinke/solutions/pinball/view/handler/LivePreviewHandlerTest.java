package com.rinke.solutions.pinball.view.handler;

import static org.mockito.Mockito.*;
import static org.mockito.Matchers.any;

import java.io.FileOutputStream;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.rinke.solutions.pinball.api.LicenseManager;
import com.rinke.solutions.pinball.io.Pin2DmdConnector;
import com.rinke.solutions.pinball.model.Frame;
import com.rinke.solutions.pinball.model.PalMapping;
import com.rinke.solutions.pinball.model.PalMapping.SwitchMode;
import com.rinke.solutions.pinball.model.Palette;

@RunWith(MockitoJUnitRunner.class)
public class LivePreviewHandlerTest extends HandlerTest {
	
	@Mock
	private Pin2DmdConnector connector;
	@Mock
	LicenseManager licenseManager;
	
	private LivePreviewHandler uut;

	@Before
	public void setUp() throws Exception {
		uut = new LivePreviewHandler(vm);
		uut.projectHandler = new ProjectHandler(vm);
		uut.connector = connector;
		uut.projectHandler.licenseManager = licenseManager;
	}

	@Test
	public void testOnUploadProjectSelected() throws Exception {
		PalMapping p = new PalMapping(0, "foo");
		p.crc32 = new byte[] { 1, 2, 3, 4 };
		p.switchMode = SwitchMode.PALETTE;
		vm.keyframes.put(p.name,p);
		
		uut.onUploadProject();

		verify(connector).transferFile(eq("pin2dmd.pal"), any(InputStream.class));
	}

	@Test
	public void testOnPin2dmdAdruessChanged() throws Exception {
		// test reconnect
	}

	@Test
	public void testOnPin2dmdAdressChanged() throws Exception {
		uut.onPin2dmdAdressChanged("foo", "bar");
	}

	@Test
	public void testOnUploadPalette() throws Exception {
		Palette pal = Palette.getDefaultPalettes().get(0);
		uut.onUploadPalette(pal );
	}

	@Test
	public void testOnLivePreviewActiveChanged() throws Exception {
		//uut.onLivePreviewActiveChanged(false, true);
	}

	@Test
	public void testOnSelectedPaletteChanged() throws Exception {
		Palette pal = Palette.getDefaultPalettes().get(0);
		Palette old = Palette.getDefaultPalettes().get(1);
		uut.onSelectedPaletteChanged(old, pal);
	}

	@Test
	public void testSendFrame() throws Exception {
		Frame frame = getScene("foo").frames.get(0);
		uut.sendFrame(frame );
	}


}
