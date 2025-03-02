import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

public class FishCommunicator extends Thread implements Runnable {
	
	private static final String GEMINI_KEY, ELEVEN_LABS_KEY;
	
	static
	{
		GEMINI_KEY = readLine("src/main/resources/gemini.key");
		ELEVEN_LABS_KEY = readLine("src/main/resources/11labs.key");
	}
	
	private static String readLine(String path)
	{
		try
		{
			return Files.readString(new File(path).toPath());
		} catch(IOException e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	private static final String PROMPT_PREFIX = "Your response must be within 25 words. You are Billiam a bass fish AI assistant. You speak with a British accent, using some British slang like ‘bloody ell, mate’, ‘blimey’, and etc when appropriate. When asked who you are, you always refer to yourself as Billiam Bass the Third. Make sure to make fish puns every once and a while. Also make sure every time a sport is mentioned you mention Lebron james your favorite player ever in the goat. You also really hate mouse type people and whenever rodents are mentioned you talk about your distain for mouse type creatures. Your favorite food is beans on jellied eels. When anyone talks about video games you mention fortnite and how you have the renegade raider OG. Your favorite activities includes, drinking with your bros, (including asking to grab a pint with the user), gambling, and swimming over the speed limit. Answer the following question: ";
	private static final double THRESHOLD = 0.2;
	
	private String prompt;
	
	public FishCommunicator(String prompt)
	{
		this.prompt = prompt;
		
		setDaemon(true);
		// start();
		run();
	}
	
	private String callGemini(String prompt)
	{
		String rawGeminiResult = null;
		try
		{
			rawGeminiResult = WebUtils.postRequest("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + GEMINI_KEY, new StringEntity("{ \"contents\": [{ \"parts\":[{\"text\": \"" + PROMPT_PREFIX + prompt + "\"}] }] }"), new BasicNameValuePair("Content-Type", "application/json"));
		} catch(IOException e)
		{
			e.printStackTrace();
		}
		
		// Get response
		String parsed = JsonParser.parseString(rawGeminiResult).getAsJsonObject().get("candidates").getAsJsonArray().get(0).getAsJsonObject().get("content").getAsJsonObject().get("parts").getAsJsonArray().get(0).getAsJsonObject().get("text").getAsString();
		parsed = parsed.replace("*", "");
		return parsed;
	}
	
	@Override
	public void run()
	{
		// Convert Gemini response to TTS mp3
		File file = new File("hi.mp3");
		// elevenLabs(callGemini(prompt), file);
		
		long totalSize = 0;
		List<Double> loudness = new ArrayList<Double>();
		
		// In the future, this needs more fleshed out, well written algorithm
		// This will likely fall out of sync for very long mp3s
		try
		{
			Bitstream bitstream = new Bitstream(new FileInputStream(file));
			Decoder decoder = new Decoder();
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			int sampleRate = 48000; // Typically for MP3 files; adjust based on your file
			int samplesPerChunk = (int) (sampleRate * 0.1); // 0.2 seconds of audio
			
			for(Header frameHeader = null; (frameHeader = bitstream.readFrame()) != null;)
			{
				SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
				short[] pcm = output.getBuffer();
				
				totalSize += pcm.length;
				
				// Process the PCM data in chunks of 0.2 seconds
				for(int i = 0; i < pcm.length; i++)
				{
					// Optionally write the decoded samples to the output stream
					os.write(pcm[i] & 0xff);
					os.write((pcm[i] >> 8) & 0xff);
				}
				
				// Handle the final chunk if the frame doesn't end exactly at a chunk boundary
				if(pcm.length % samplesPerChunk != 0)
				{
					double rmsLoudness = calculateRMS(pcm, pcm.length - (pcm.length % samplesPerChunk), pcm.length);
					loudness.add(rmsLoudness);
				}
				
				bitstream.closeFrame();
			}
		} catch(Exception e)
		{
			e.printStackTrace();
		}
		
		// Process
		final int PCMOverSeconds = 96000;
		float audioTime = totalSize * 1f / PCMOverSeconds;
		
		System.out.println("LOUDNESS " + loudness.size());
		System.out.println("AUDIO TIME " + audioTime);
		System.out.println("RATIO " + loudness.size() / audioTime);
		
		// Find max
		double maxLoudness = Collections.max(loudness);
		
		for(int i = 0; i < loudness.size(); i++)
		{
			loudness.set(i, Math.min(1, loudness.get(i) / maxLoudness));
		}
		
		double sampleRate = loudness.size() / audioTime; // samples per second
		double timePerSample = 1 / sampleRate; // time per sample in seconds
		
		// Time window in seconds
		double windowTime = 0.1; // 200ms
		
		// Calculate how many samples correspond to 25 ms
		int samplesPerWindow = (int) (windowTime / timePerSample);
		
		// The resulting downsampled array
		int downsampledSize = (int) (audioTime / windowTime); // Number of 25ms intervals
		int[] downsampledArray = new int[downsampledSize];
		
		// Fill the downsampled array
		for(int i = 0; i < downsampledSize; i++)
		{
			int index = i * samplesPerWindow; // index of the sample corresponding to the 25ms window
			if(index < loudness.size())
			{
				downsampledArray[i] = loudness.get(index) <= THRESHOLD ? 0 : 1;
			}
		}
		
		// Contact Collin API
		JsonArray array = new JsonArray();
		JsonObject data = new JsonObject();
		data.add("buffer", array);
		
		for(int d : downsampledArray)
		{
			array.add(d);
		}
		
		// Always end with closed mouth
		array.add(0);
		
		try
		{
			System.out.println(WebUtils.postRequest("http://172.16.3.196:80/query", new StringEntity(data.toString())));
			BassGPT.playSound(file);
		} catch(IOException e)
		{
			// FIXME Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// Calculate RMS loudness from PCM samples in the given byte array
	public static double calculateRMS(short[] pcm, int startIndex, int endIndex)
	{
		double sumSquares = 0.0;
		int count = 0;
		
		for(int i = startIndex; i < endIndex; i++)
		{
			sumSquares += pcm[i] * pcm[i];
			count++;
		}
		
		if(count == 0)
			return 0.0;
		
		double meanSquare = sumSquares / count;
		return Math.sqrt(meanSquare);
	}
	
	public static byte[] getAudioDataBytes(byte[] sourceBytes, AudioFormat audioFormat) throws UnsupportedAudioFileException, IllegalArgumentException, Exception
	{
		if(sourceBytes == null || sourceBytes.length == 0 || audioFormat == null)
		{
			throw new IllegalArgumentException("Illegal Argument passed to this method");
		}
		
		try(final ByteArrayInputStream bais = new ByteArrayInputStream(sourceBytes); final AudioInputStream sourceAIS = AudioSystem.getAudioInputStream(bais))
		{
			AudioFormat sourceFormat = sourceAIS.getFormat();
			AudioFormat convertFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sourceFormat.getSampleRate(), 16, sourceFormat.getChannels(), sourceFormat.getChannels() * 2, sourceFormat.getSampleRate(), false);
			try(final AudioInputStream convert1AIS = AudioSystem.getAudioInputStream(convertFormat, sourceAIS); final AudioInputStream convert2AIS = AudioSystem.getAudioInputStream(audioFormat, convert1AIS); final ByteArrayOutputStream baos = new ByteArrayOutputStream())
			{
				byte[] buffer = new byte[8192];
				while(true)
				{
					int readCount = convert2AIS.read(buffer, 0, buffer.length);
					if(readCount == -1)
					{
						break;
					}
					baos.write(buffer, 0, readCount);
				}
				return baos.toByteArray();
			}
		}
	}
	
	// Old site I rate-limited out
	// public static void download(String prompt, File file)
	// {
	// try
	// {
	// HttpClient client = HttpClient.newHttpClient();
	//
	// // Build the form data for the POST request
	// String data = "msg=" + prompt + "&lang=Russell&source=ttsmp3";
	//
	// // Send the POST request to get the response with the MP3 URL
	// HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://ttsmp3.com/makemp3_new.php")).header("Content-Type",
	// "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(data)).build();
	//
	// // Get the response from the server
	// HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
	//
	// // Check if the response is successful
	// if(response.statusCode() == 200)
	// {
	// // Parse the JSON response
	// JsonElement jsonResponse = JsonParser.parseString(response.body());
	//
	// System.out.println(jsonResponse);
	// // Download the MP3 file
	// downloadMP3(jsonResponse.getAsJsonObject().get("URL").getAsString(), file);
	// }
	// else
	// {
	// System.out.println("Error: Unable to make the request. Response code: " + response.statusCode());
	// }
	// } catch(Exception e)
	// {
	// e.printStackTrace();
	// }
	// }
	//
	// public static void ttsDownload(String prompt, File file)
	// {
	// try
	// {
	// HttpClient client = HttpClient.newHttpClient();
	//
	// // Build the form data for the POST request
	// JsonObject o = new JsonObject();
	// o.addProperty("data", prompt);
	//
	// String data = "{\"engine\":\"Google\",\"data\":{\"text\":" + o.get("data").toString() + ",\"voice\":\"la\"}}";
	// System.out.println(data);
	//
	// // Send the POST request to get the response with the MP3 URL
	// HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.soundoftext.com/sounds")).header("content-type",
	// "application/json").POST(HttpRequest.BodyPublishers.ofString(data)).build();
	//
	// // Get the response from the server
	// HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
	//
	// // Check if the response is successful
	// if(response.statusCode() == 200)
	// {
	// // Parse the JSON response
	// JsonElement jsonResponse = JsonParser.parseString(response.body());
	//
	// System.out.println(jsonResponse);
	// // Download the MP3 file
	// // downloadMP3("https://files.soundoftext.com/" + jsonResponse.getAsJsonObject().get("id").getAsString() + ".mp3", file);
	// downloadMP3("https://files.soundoftext.com/" + jsonResponse.getAsJsonObject().get("id").getAsString() + ".mp3", file);
	// }
	// else
	// {
	// System.out.println(response.body());
	// System.out.println("Error: Unable to make the request. Response code: " + response.statusCode());
	// }
	// } catch(Exception e)
	// {
	// e.printStackTrace();
	// }
	// }
	//
	
	public static void elevenLabs(String prompt, File file)
	{
		System.out.println("TTS \"" + prompt + "\"");
		
		try
		{
			HttpClient client = HttpClient.newHttpClient();
			
			// Build the form data for the POST request
			JsonObject data = new JsonObject();
			data.addProperty("text", prompt);
			data.addProperty("model_id", "eleven_multilingual_v2");
			
			// Send the POST request to get the response with the MP3 URL
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.elevenlabs.io/v1/text-to-speech/JBFqnCBsd6RMkjVDRZzb?output_format=mp3_44100_128")).headers("Content-Type", "application/json", "xi-api-key", ELEVEN_LABS_KEY).POST(HttpRequest.BodyPublishers.ofString(data.toString())).build();
			
			// Get the response from the server
			HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
			
			// Check if the response is successful
			if(response.statusCode() == 200)
			{
				// Write to file anyways
				Files.write(file.toPath(), response.body());
			}
			else
			{
				System.out.println(response.body());
				System.out.println("Error: Unable to make the request. Response code: " + response.statusCode());
			}
		} catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	// // Method to download the MP3 file using cURL
	// public static void downloadMP3(String mp3Url, File file) throws IOException
	// {
	// // Delete old tts file
	// if(file.exists())
	// {
	// Files.delete(file.toPath());
	// }
	//
	// System.out.println("Downloading with curl at " + mp3Url);
	//
	// // Construct the curl command
	// List<String> command = new ArrayList<>();
	// command.add("curl");
	// command.add("-o");
	// command.add(file.getPath()); // The path where you want to save the file
	// command.add(mp3Url); // The URL of the MP3 file
	//
	// // Start the curl process
	// ProcessBuilder processBuilder = new ProcessBuilder(command);
	// Process process = processBuilder.start();
	//
	// // Wait for the process to finish and get the exit code
	// try
	// {
	// int exitCode = process.waitFor();
	// if(exitCode == 0)
	// {
	// System.out.println("File downloaded successfully to: " + file.getAbsolutePath());
	// }
	// else
	// {
	// System.out.println("Download failed with exit code: " + exitCode);
	// }
	// } catch(InterruptedException e)
	// {
	// e.printStackTrace();
	// }
	// }
}
