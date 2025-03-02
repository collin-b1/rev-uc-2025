import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.stream.Stream;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

import com.fazecast.jSerialComm.SerialPort;
import com.google.gson.JsonObject;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

public class BassGPT {
	
	public static void main(String[] args) throws IOException
	{
		final int padding = 5;
		
		JFrame frame = new JFrame("BassGPT");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(600, 400);
		frame.setLocationRelativeTo(null);
		frame.setLayout(null);
		
		// Label
		JLabel label = new JLabel("Ask Billy.", SwingConstants.CENTER);
		label.setBounds(0, 0, frame.getWidth(), 50);
		frame.add(label);
		
		// Text area
		JTextArea area = new JTextArea();
		area.setBounds(padding, label.getY() + label.getHeight(), frame.getWidth() - padding * 2, frame.getHeight() - label.getY() - label.getHeight() - padding - 23); // -23 is mac specific
		frame.add(area);
		
		frame.setVisible(false);
		
		// Find available COM ports
		SerialPort[] ports = SerialPort.getCommPorts();
		if(ports.length == 0)
		{
			System.out.println("No serial ports found!");
			return;
		}
		
		// Use the first available port (COM5, change as needed)
		SerialPort serialPort = Stream.of(ports).filter(port -> port.getDescriptivePortName().contains("USB Serial")).findFirst().orElse(null);
		
		if(serialPort == null)
		{
			System.out.println("Port not found");
			return;
		}
		serialPort.setBaudRate(9600);
		serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
		
		if(!serialPort.openPort())
		{
			System.out.println("Failed to connect with the BillyControlSystem.");
			return;
		}
		
		System.out.println("BillyControlSystem connection established on " + serialPort.getSystemPortName());
		
		// Start listening for incoming data from the serial port
		try(InputStream inputStream = serialPort.getInputStream(); Scanner scanner = new Scanner(inputStream))
		{
			while(scanner.hasNextLine())
			{
				switch(scanner.nextLine().trim())
				{
					case "BUTTON_PRESSED":
					{
						// Prompt for input
						String prompt = area.getText();
						if(prompt == null || prompt.isBlank())
							prompt = "Introduce yourself!";
						
						if(frame.isVisible())
						{
							fireTorso(false);
							BassGPT.playSound(new File("ping.mp3"));
							// Hide frame while fish question is being asked
							frame.setVisible(false);
							// Fire request
							new FishCommunicator(prompt);
						}
						else
						{
							// Hide frame while fish question is being asked
							fireTorso(true);
							frame.setVisible(true);
						}
						
						area.setText("");
						break;
					}
					case "KEY_TURNED_ON":
					{
						fireTorso(true);
						frame.setVisible(true);
						break;
					}
					case "KEY_TURNED_OFF":
					{
						fireTorso(false);
						frame.setVisible(false);
						area.setText("");
						break;
					}
				}
			}
		} finally
		{
			// Close the serial port after the communication ends
			serialPort.closePort();
			System.out.println("Billiam has disconnected the serial port.");
		}
	}
	
	private static void fireTorso(boolean on)
	{
		JsonObject object = new JsonObject();
		object.addProperty("value", on);
		System.out.println(WebUtils.postRequest("http://172.16.3.196:80/torso", object.toString()));
	}
	
	public static void playSound(File file)
	{
		Thread thread = new Thread(() ->
		{
			Player player;
			try
			{
				player = new Player(new FileInputStream(file));
				player.play();
			} catch(FileNotFoundException | JavaLayerException e)
			{
				e.printStackTrace();
			}
		});
		
		// thread.setDaemon(true);
		thread.start();
	}
}
