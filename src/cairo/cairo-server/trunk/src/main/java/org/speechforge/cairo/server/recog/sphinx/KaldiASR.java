package org.speechforge.cairo.server.recog.sphinx;
//

// Copyright 2013 Polish-Japanese Institute of Information Technology (author: Danijel Korzinek)

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// THIS CODE IS PROVIDED *AS IS* BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, EITHER EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION ANY IMPLIED
// WARRANTIES OR CONDITIONS OF TITLE, FITNESS FOR A PARTICULAR PURPOSE,
// MERCHANTABLITY OR NON-INFRINGEMENT.
// See the Apache 2 License for the specific language governing permissions and
// limitations under the License.

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.log4j.Logger;
import org.speechforge.cairo.util.pool.AbstractPoolableObject;

public class KaldiASR extends AbstractPoolableObject {
	
	protected static final Logger _logger = Logger.getLogger(KaldiASR.class);

	private Socket socket;

	private OutputStream output_stream;
	private BufferedReader input_reader;

	enum OutputFormat {
		WORDS, WORDS_ALIGNED
	}

	KaldiASR(String host, int port) throws UnknownHostException, IOException {
		_logger.info("@@@KALDI-REMOTE: Attaching to "+host+" via "+port+" port.\n");
		socket = new Socket(host, port);
		output_stream = socket.getOutputStream();
		input_reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), Options.KALDI_ENCODING));
	}

	public void recognize(InputStream input_stream, long input_size, OutputProcess output_process) throws IOException {
_logger.debug("KALDI recognize: 1");

		new Thread(new StreamSender(input_stream, input_size, output_stream)).start();

_logger.debug("KALDI recognize: 2");
		float total_reco_dur = 0, total_input_dur = 0;

_logger.debug("KALDI recognize: 3");
		while (true) {
			String header = input_reader.readLine();
_logger.debug("header="+header);
_logger.debug("KALDI recognize: 4");

			if (header == null)
				throw new RuntimeException("Error parsing header #1");

_logger.debug("KALDI recognize: 5");
			if (!header.startsWith("RESULT:")) {
				if (header.startsWith("PARTIAL:")) {
					_logger.info(header.substring(8));
					continue;
				}
				throw new RuntimeException("Error parsing header #2");
			}

_logger.debug("KALDI recognize: 6");
			if (header.substring(7).equals("DONE"))
				break;

_logger.debug("KALDI recognize: 7");
			String params[] = header.substring(7).split(",");

_logger.debug("KALDI recognize: 8");
			int num = 0;
			float reco_dur = 0, input_dur = 0;
_logger.debug("KALDI recognize: 9");
			OutputFormat format = OutputFormat.WORDS;

_logger.debug("KALDI recognize: 10");
			for (String param : params) {
				String ptok[] = param.split("=");

				if (ptok[0].equals("NUM"))
					num = Integer.parseInt(ptok[1]);
				else if (ptok[0].equals("RECO-DUR"))
					reco_dur = Float.parseFloat(ptok[1]);
				else if (ptok[0].equals("INPUT-DUR"))
					input_dur = Float.parseFloat(ptok[1]);
				else if (ptok[0].equals("FORMAT")) {
					if (ptok[1].equals("WSE"))
						format = OutputFormat.WORDS_ALIGNED;
					else if (ptok[1].equals("W"))
						format = OutputFormat.WORDS;
					else
						throw new RuntimeException("Output format " + ptok[1] + " is not supported");
				} else
					_logger.info("WARNING: unknown parameter in header: " + ptok[0]);
			}

_logger.debug("KALDI recognize: 12");
			String reco_words = "";
			for (int i = 0; i < num; i++) {
				String line = input_reader.readLine();
				if (format == OutputFormat.WORDS_ALIGNED) {
					String word[] = line.split(",");
					if (output_process != null)
						output_process.addWord(new OutputProcess.Word(word[0], Float.parseFloat(word[1]), Float
								.parseFloat(word[2])));
					reco_words += " " + word[0];
				} else if (format == OutputFormat.WORDS) {
					if (output_process != null)
						output_process.addWord(new OutputProcess.Word(line, 0, 0));
					reco_words += " " + line;
				}
			}

_logger.debug("KALDI recognize: 13");
			total_input_dur += input_dur;
			total_reco_dur += reco_dur;

_logger.debug("KALDI recognize: 14");
			_logger.info("Recognized:" + reco_words);
			_logger.info("Speed: " + (input_dur / reco_dur) + "x RT");

		}

		_logger.info("Total speed: " + (total_input_dur / total_reco_dur) + "x RT");

	}

	public String recognize2(InputStream input_stream, long input_size) throws IOException {
_logger.debug("KALDI recognize: 1");

		new Thread(new StreamSender(input_stream, input_size, output_stream)).start();

_logger.debug("KALDI recognize: 2");
		float total_reco_dur = 0, total_input_dur = 0;

_logger.debug("KALDI recognize: 3");
			String header = input_reader.readLine();
if (header.length() >= 4)
{
  header=header.substring(2,header.length()-2);
}
_logger.debug("header="+header);
_logger.debug("KALDI recognize: 4a");
return header;
	}

	public void close() throws IOException {
		output_stream.close();
		input_reader.close();
		socket.close();
	}
}
