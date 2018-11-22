package com.webtech.FacebookWatsonBot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.github.messenger4j.Messenger;
import com.github.messenger4j.exception.MessengerApiException;
import com.github.messenger4j.exception.MessengerIOException;
import com.github.messenger4j.exception.MessengerVerificationException;
import com.github.messenger4j.messengerprofile.persistentmenu.action.CallToAction.Type;
import com.github.messenger4j.send.MessagePayload;
import com.github.messenger4j.send.MessagingType;
import com.github.messenger4j.send.message.RichMediaMessage;
import com.github.messenger4j.send.message.TextMessage;
import com.github.messenger4j.send.message.richmedia.UrlRichMediaAsset;
import com.github.messenger4j.webhook.event.TextMessageEvent;
import com.ibm.watson.developer_cloud.assistant.v2.Assistant;
import com.ibm.watson.developer_cloud.assistant.v2.model.CreateSessionOptions;
import com.ibm.watson.developer_cloud.assistant.v2.model.MessageContext;
import com.ibm.watson.developer_cloud.assistant.v2.model.MessageInput;
import com.ibm.watson.developer_cloud.assistant.v2.model.MessageOptions;
import com.ibm.watson.developer_cloud.assistant.v2.model.MessageResponse;
import com.ibm.watson.developer_cloud.assistant.v2.model.SessionResponse;
import com.ibm.watson.developer_cloud.service.security.IamOptions;

/**
 * Root resource (exposed at "myresource" path)
 */
@Path("myresource")
public class MyResource {
	// Facebook credentials
	private final static String pageAccessToken="EAAQ2LHfnEEUBAKtk9AN7OAZAliegpls0hJQiJkOuS39BOldl0SyBBVaQtJk2leMlQvn4eLEVq800vMTbVUz2lGoX3zvV2fNYDAM6ZC4scpa9UsJBZB9g2aJybm7t4sG6yRNq3q17ILisgXdEctGTC5RZAvUS04VF9qdSq8SBkQZDZD";
	private final static String appSecret="59823206bc62f201b57bed1b4ac501b5";
	private final static String verifyToken="watson";
	// Watson credentials
	private final static String apikey = "R42xhNCMrb7Gl7DtToIa_Wqm3LktGcSi9J1G9Nuz0TqB";
	private final static String assistantId = "09d4182d-f5a9-4799-8b26-99e009ae2f2f";
	private final static String assistantUrl = "https://gateway-fra.watsonplatform.net/assistant/api";
	private final static String assistantVer = "2018-11-08";
	// Messenger4j instance
	private final Messenger messenger = Messenger.create(pageAccessToken, appSecret, verifyToken);
	// Watson instances
    private final IamOptions iamOptions = new IamOptions.Builder().apiKey(apikey).build();
    private final Assistant service = new Assistant(assistantVer, iamOptions);
    private final CreateSessionOptions createSessionOptions = new CreateSessionOptions.Builder(assistantId).build();
    private MessageContext context = new MessageContext();
    // a server storage used to store the user id and Watson's session id
    SingletonStorage storage = SingletonStorage.getInstance();
	
    /**
     * Function that handles Facebook webhook verification
     * uses messenger4j to verify the query parameters
     * @return Response OK or Not Accepted.
     */
    @GET
    @Path("webhook")
    public Response verifyWebhook(@QueryParam("hub.mode") String mode, @QueryParam("hub.verify_token") String token, @QueryParam("hub.challenge") String challenge) {
    	try {
    		messenger.verifyWebhook(mode, token);
    		return Response.ok(challenge).build();
    	} catch(MessengerVerificationException | IllegalArgumentException e) {
    		System.out.println("Error with messenger verification.");
    	}
		return Response.notAcceptable(null).build();
    }
    
    /**
     * Main function that handles the bot requests and responses
     * @return Response OK
     * @throws IOException
     */
    @POST
    @Path("webhook")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response handleCallback(@Context HttpServletRequest request, InputStream requestBody) throws IOException {
    	
    	// building the payload
        BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(line);
        }
        
        // building Facebook messenger signature
        String sigParam = request.getHeader( "X-Hub-Signature");
        Optional<String> signature = Optional.ofNullable(sigParam).filter(s -> !s.isEmpty());
        
        System.out.println("Received Messenger Platform callback - payload: {"+out.toString()+"} | signature: {"+signature+"}");
        
        try {
        	messenger.onReceiveEvents(out.toString(), signature, event -> {
        		final String senderId = event.senderId();
        		final Instant timestamp = event.timestamp();
        		MessagePayload payload;
        		
        		// If it's a simple text message
        		if (event.isTextMessageEvent()) {
        			final TextMessageEvent textMessageEvent = event.asTextMessageEvent();
        			final String messageId = textMessageEvent.messageId();
        			final String text = textMessageEvent.text();
        			
        			System.out.println("Received text message from '{"+senderId+"}' at '{"+timestamp+"}' with content: {"+text+"} (mid: {"+messageId+"})");
        			
        			String watsonResponse = getWatsonResponse(text, senderId);
        			System.out.println("Watson Response is = "+watsonResponse);
        			
        			// Watson wants to send a picture to the user
        			if (watsonResponse.endsWith(".jpg")) {
						try {
							UrlRichMediaAsset richMediaAsset = UrlRichMediaAsset.create(com.github.messenger4j.send.message.richmedia.RichMediaAsset.Type.IMAGE, new URL(watsonResponse));
							final RichMediaMessage richMediaMessage = RichMediaMessage.create(richMediaAsset);
							payload = MessagePayload.create(senderId, MessagingType.RESPONSE,richMediaMessage);
						} catch (MalformedURLException e) {
							e.printStackTrace();
							payload = MessagePayload.create(senderId, MessagingType.RESPONSE, TextMessage.create("Oops. It seems I have some issues :("));
						}
        			}
        			
        			else {
        				payload = MessagePayload.create(senderId, MessagingType.RESPONSE, TextMessage.create(watsonResponse));
        			}
        			
					try {
						messenger.send(payload);
					} catch (MessengerApiException | MessengerIOException e) {
						e.getMessage();
					}
        		}
        	});
        }catch (MessengerVerificationException | IllegalArgumentException e) {
        	System.out.println(e.getMessage());
        	return Response.ok().build();
        }
        
        reader.close();
        return Response.ok().build();
    }
    
    /**
     * Function that handles Watson Assistant integration
     * @param inputText = the input from the user
     * @param senderId = the user's id
     * @return a String which is the assistant's response
     */
    public String getWatsonResponse(String inputText, String senderId) {
    	service.setEndPoint(assistantUrl);

    	// Checks if the user already started a conversion with the bot, if not then creates a new session and stores the ids
    	if (!storage.getWatsonSessionIds().containsKey(senderId)) {
    		System.out.println("creating a new watson session!!!");
		    SessionResponse session = service.createSession(createSessionOptions).execute();
		    String sessionId = session.getSessionId();
		    storage.getWatsonSessionIds().put(senderId, sessionId);
    	}

        // Send message to assistant
        MessageInput input = new MessageInput.Builder().text(inputText).build();
        MessageOptions messageOptions = new MessageOptions.Builder(assistantId, storage.getWatsonSessionIds().get(senderId))
                                                  .input(input)
                                                  .context(context)
                                                  .build();
        
        // Gets the assistant response
        MessageResponse response = service.message(messageOptions).execute();
        context = response.getContext();
        
        String watsonResponse = response.getOutput().getGeneric().get(0).getText();
        
        if (watsonResponse == null || watsonResponse.isEmpty()) {
        	String responseType = response.getOutput().getGeneric().get(0).getResponseType();
        	if (responseType.equals("image")) {
        		watsonResponse = response.getOutput().getGeneric().get(0).getSource();
        	}
        }
        
    	return watsonResponse;
    }
}