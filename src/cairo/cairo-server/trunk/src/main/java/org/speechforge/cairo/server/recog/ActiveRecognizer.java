package org.speechforge.cairo.server.recog;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.media.protocol.PushBufferDataSource;

import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.speechforge.cairo.server.recog.sphinx.KaldiRecEngineWFST;

public class ActiveRecognizer {

	static Logger _logger = Logger.getLogger(ActiveRecognizer.class);

	private ObjectPool _recPool;
	private Object _recEngine;
	private String _appType;

	public ActiveRecognizer(ObjectPool recPool, Object recEngine, String appType) {
		_recPool = recPool;
		_recEngine = recEngine;
		_appType = appType;
	}

	public void startRecognition(PushBufferDataSource dataSource,
			RecogListener recogListener) throws UnsupportedEncodingException {
		if (_appType.equals("application/wfst")) {
			((KaldiRecEngineWFST) _recEngine).startRecognition(dataSource,
					recogListener);
			((KaldiRecEngineWFST) _recEngine).startRecogThread();
		} else {
			// TODO: Unsupported application type
		}
	}

	public void loadLM(GrammarLocation grammarLocation)
			throws IOException {
		if (_appType.equals("application/wfst")) {
			((KaldiRecEngineWFST) _recEngine).load(grammarLocation);
		} else {
			// TODO: Unsupported application type
		}
	}
	
	public void deallocateLM(){
		if (_appType.equals("application/wfst")) {
			// DO NOTHING
			//((KaldiRecEngineWFST) _recEngine).deallocateJSGF();
		} else if (_appType.equals("application/x-jsgf")) {
			// TODO: Unsupported application type
		}
	}

	public void setHotword(boolean hotword) {
		if (_appType.equals("application/wfst")) {
			((KaldiRecEngineWFST) _recEngine).setHotword(hotword);
		} else if (_appType.equals("application/x-jsgf")) {
			// TODO: Unsupported.
			//((SphinxRecEngineJSGF) _recEngine).setHotword(hotword);
		} else {
			// TODO: Unsupported.
		}
	}

	public void returnRecEngine() throws Exception {
		deallocateLM();
		_recPool.returnObject(_recEngine);
	}

}
