package com.chameleonvision.vision.camera;

import com.chameleonvision.settings.Platform;
import com.chameleonvision.vision.Pipeline;
import com.chameleonvision.web.ServerHandler;
import edu.wpi.cscore.*;
import edu.wpi.first.cameraserver.CameraServer;
import org.opencv.core.Mat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.IntStream;

public class Camera {

	private static final float DEFAULT_FOV = 60.8f;
	private static final int MINIMUM_FPS = 30;
	private static final int MINIMUM_WIDTH = 320;
	private static final int MINIMUM_HEIGHT = 200;
	private static final int MAX_INIT_MS = 1500;

	public final String name;
	public final String path;

	private final UsbCamera UsbCam;
	private final VideoMode[] availableVideoModes;

	private final CameraServer cs = CameraServer.getInstance();
	private final CvSink cvSink;
	private final Object cvSourceLock = new Object();
	private CvSource cvSource;
	private float FOV;
	private CameraValues camVals;
	private CamVideoMode camVideoMode;
	private int currentPipelineIndex;
	private HashMap<Integer, Pipeline> pipelines;

	public Camera(String cameraName) {
		this(cameraName, DEFAULT_FOV);
	}

	public Camera(String cameraName, float fov) {
		this(cameraName,CameraManager.AllUsbCameraInfosByName.get(cameraName), fov);
	}

	public Camera(String cameraName, UsbCameraInfo usbCamInfo, float fov) {
		this(cameraName ,usbCamInfo, fov, new HashMap<>(), 0);
	}

	public Camera(String cameraName, float fov, int videoModeIndex) {
		this(cameraName, fov, new HashMap<>(), videoModeIndex);
	}

	public Camera(String cameraName, float fov, HashMap<Integer, Pipeline> pipelines, int videoModeIndex) {
		this(cameraName, CameraManager.AllUsbCameraInfosByName.get(cameraName), fov, pipelines, videoModeIndex);
	}

	public Camera(String cameraName, UsbCameraInfo usbCamInfo, float fov, HashMap<Integer, Pipeline> pipelines, int videoModeIndex) {
		FOV = fov;
		name = cameraName;
		path = usbCamInfo.path;

		UsbCam = new UsbCamera(name, path);

		this.pipelines = pipelines;

		// set up video modes according to minimums
		if (Platform.getCurrentPlatform() == Platform.WINDOWS_64 && !UsbCam.isConnected()) {
			System.out.print("Waiting on camera... ");
			long initTimeout = System.nanoTime();
			while(!UsbCam.isConnected())
			{
				if (((System.nanoTime() - initTimeout)  / 1e6 ) >= MAX_INIT_MS) {
					break;
				}
			}
			var initTimeMs = (System.nanoTime() - initTimeout) / 1e6;
			System.out.printf("Camera initialized in %.2fms\n", initTimeMs);
		}
		var trueVideoModes = UsbCam.enumerateVideoModes();
		availableVideoModes = Arrays.stream(trueVideoModes).filter(v -> v.fps >= MINIMUM_FPS && v.width >= MINIMUM_WIDTH && v.height >= MINIMUM_HEIGHT).toArray(VideoMode[]::new);
		if (availableVideoModes.length == 0) {
			System.err.println("Camera not supported!");
			throw new RuntimeException(new CameraException(CameraException.CameraExceptionType.BAD_CAMERA));
		}
		if (videoModeIndex <= availableVideoModes.length - 1) {
			setCamVideoMode(videoModeIndex, false);
		} else {
			setCamVideoMode(0, false);
		}

		cvSink = cs.getVideo(UsbCam);
		cvSource = cs.putVideo(name, camVals.ImageWidth, camVals.ImageHeight);
	}

	VideoMode[] getAvailableVideoModes() {
		return availableVideoModes;
	}

	public int getStreamPort() {
		var s = (MjpegServer) cs.getServer("serve_" + name);
		return s.getPort();
	}

	public void setCamVideoMode(int videoMode, boolean updateCvSource) {
		setCamVideoMode(new CamVideoMode(availableVideoModes[videoMode]), updateCvSource);
	}

	private void setCamVideoMode(CamVideoMode newVideoMode, boolean updateCvSource) {
		var prevVideoMode = this.camVideoMode;
		this.camVideoMode = newVideoMode;
		UsbCam.setVideoMode(newVideoMode.getActualPixelFormat(), newVideoMode.width, newVideoMode.height, newVideoMode.fps);

		// update camera values
		camVals = new CameraValues(this);
		if (prevVideoMode != null && !prevVideoMode.equals(newVideoMode) && updateCvSource) { //  if resolution changed
			synchronized (cvSourceLock) {
				cvSource = cs.putVideo(name, newVideoMode.width, newVideoMode.height);
			}
			ServerHandler.sendFullSettings();

		}
	}

	void addPipeline() {
		addPipeline(pipelines.size());
	}

	private void addPipeline(int pipelineNumber) {
		if (pipelines.containsKey(pipelineNumber)) return;
		pipelines.put(pipelineNumber, new Pipeline());
	}

	public Pipeline getCurrentPipeline() {
		return pipelines.get(currentPipelineIndex);
	}

	public int getCurrentPipelineIndex() {
		return currentPipelineIndex;
	}

	public void setCurrentPipelineIndex(int pipelineNumber) {
		if (pipelineNumber - 1 > pipelines.size()) return;
		currentPipelineIndex = pipelineNumber;
	}

	public HashMap<Integer, Pipeline> getPipelines() {
		return pipelines;
	}

	public CamVideoMode getVideoMode() {
		return camVideoMode;
	}

	public int getVideoModeIndex() {
		return IntStream.range(0, availableVideoModes.length)
				.filter(i -> camVideoMode.equals(availableVideoModes[i]))
				.findFirst()
				.orElse(-1);
	}

	public float getFOV() {
		return FOV;
	}

	public void setFOV(float fov) {
		FOV = fov;
		camVals = new CameraValues(this);
	}

	public int getBrightness() {
		return getCurrentPipeline().brightness;
	}

	public void setBrightness(int brightness) {
		getCurrentPipeline().brightness = brightness;
		UsbCam.setBrightness(brightness);
	}

	public void setExposure(int exposure) {
		getCurrentPipeline().exposure = exposure;
		UsbCam.setExposureManual(exposure);
	}

	public long grabFrame(Mat image) {
		return cvSink.grabFrame(image);
	}

	public CameraValues getCamVals() {
		return camVals;
	}

	public void putFrame(Mat image) {
		synchronized (cvSourceLock) {
			cvSource.putFrame(image);
		}
	}
}
