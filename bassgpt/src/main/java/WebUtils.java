import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * Creates HTTP requests for interacting with third-parties.
 */
public class WebUtils {
	
	/**
	 * Makes an HTTP GET request to the provided URI.
	 * 
	 * @param uri     The URI to make the request to
	 * @param headers Optional additional headers
	 * @return Response from provided URI
	 * @throws IOException an error occurred making the request or if server returned an error code
	 */
	public static String getRequest(String uri, NameValuePair... headers)
	{
		HttpGet get = null;
		
		// Merge URISyntaxException into IOException
		try
		{
			get = new HttpGet(new URI(uri));
		} catch(URISyntaxException e)
		{
			throw new IllegalStateException(e);
		}
		
		get.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
		
		for(NameValuePair pair : headers)
		{
			get.setHeader(pair.getName(), pair.getValue());
		}
		
		return buildRequest(get);
	}
	
	/**
	 * Makes an HTTP POST request to the provided URI.
	 * 
	 * @param uri     The URI to make the request to
	 * @param entity  The HTTPEntity to attach to the request, such as a body with StringEntity.
	 * @param headers Optional additional headers
	 * @return Response from provided URI
	 * @throws IOException an error occurred making the request or if server returned an error code
	 */
	public static String postRequest(String uri, HttpEntity entity, NameValuePair... headers)
	{
		HttpPost post = null;
		
		// Merge URISyntaxException into IOException
		try
		{
			post = new HttpPost(new URI(uri));
		} catch(URISyntaxException e)
		{
			throw new IllegalStateException(e);
		}
		
		post.setEntity(entity);
		post.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
		
		for(NameValuePair pair : headers)
		{
			post.setHeader(pair.getName(), pair.getValue());
		}
		
		return buildRequest(post);
	}
	
	public static String postRequest(String uri, String body)
	{
		try
		{
			return WebUtils.postRequest(uri, new StringEntity(body));
		} catch(UnsupportedEncodingException e)
		{
			throw new IllegalStateException(e);
		}
	}
	
	public static String postRequest(String uri, NameValuePair... headers) throws IOException
	{
		return WebUtils.postRequest(uri, null, headers);
	}
	
	private static String buildRequest(HttpRequestBase request)
	{
		// Execute request and read response
		try(CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build())
		{
			HttpResponse response = client.execute(request);
			
			// Throw error for unexpected result
			StatusLine status = response.getStatusLine();
			int code = status.getStatusCode();
			
			// Get response
			StringBuilder result = new StringBuilder();
			InputStream stream = response.getEntity().getContent();
			
			for(int read = 0; (read = stream.read()) != -1;)
			{
				result.append((char) read);
			}
			
			if(code < 200 || code > 299)
			{
				throw new IllegalStateException("Error status code: " + status);
			}
			
			return result.toString();
		} catch(IOException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
