/*
 * (C) Copyright 2015-2016 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kurento.tutorial.helloworld;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.kurento.client.Composite;
import org.kurento.client.ErrorEvent;
import org.kurento.client.EventListener;
import org.kurento.client.HubPort;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.ListenerSubscription;
import org.kurento.client.MediaFlowInStateChangeEvent;
import org.kurento.client.MediaFlowOutStateChangeEvent;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.StoppedEvent;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.kurento.jsonrpc.client.JsonRpcClientNettyWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

/**
 * User session.
 *
 * @author David Fernandez (d.fernandezlop@gmail.com)
 * @author Radu Tom Vlad (rvlad@naevatec.com)
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 6.1.1
 */
public class UserSession {

  private final Logger log = LoggerFactory.getLogger(UserSession.class);

  //private static final JsonRpcClientNettyWebSocket nettyWebSocket = new JsonRpcClientNettyWebSocket("https://192.168.0.218:8443");

  private String id;
  private WebRtcEndpoint webRtcEndpoint;
  private WebRtcEndpoint webRtcEndpointAudio;
  private WebRtcEndpoint webRtcEndpointVideo;
  private RecorderEndpoint recorderEndpoint;
  private MediaPipeline mediaPipeline;
  private Date stopTimestamp;
  private Composite composite;

  public UserSession(WebSocketSession session) {
    this.id = session.getId();
  }

  public Composite getComposite(){
    return composite;
  }

  public void setComposite(Composite composite){
    this.composite = composite;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public WebRtcEndpoint getWebRtcEndpoint() {
    return webRtcEndpoint;
  }

  public WebRtcEndpoint getWebRtcEndpointAudio() {
    return webRtcEndpointAudio;
  }

  public WebRtcEndpoint getWebRtcEndpointVideo() {
    return webRtcEndpointVideo;
  }

  public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint) {
    this.webRtcEndpoint = webRtcEndpoint;
  }

  public void setWebRtcEndpointAudio(WebRtcEndpoint webRtcEndpointAudio) {
    this.webRtcEndpointAudio = webRtcEndpointAudio;
  }

  public void setWebRtcEndpointVideo(WebRtcEndpoint webRtcEndpointVideo) {
    this.webRtcEndpointVideo = webRtcEndpointVideo;
  }

  public void setRecorderEndpoint(RecorderEndpoint recorderEndpoint) {
    this.recorderEndpoint = recorderEndpoint;
  }

  public MediaPipeline getMediaPipeline() {
    return mediaPipeline;
  }

  public void setMediaPipeline(MediaPipeline mediaPipeline) {
    this.mediaPipeline = mediaPipeline;
  }

  public void addCandidate(IceCandidate candidate) {
    webRtcEndpoint.addIceCandidate(candidate);
  }

  public Date getStopTimestamp() {
    return stopTimestamp;
  }

  public void stop() {
    if (recorderEndpoint != null) {
      final CountDownLatch stoppedCountDown = new CountDownLatch(1);
      ListenerSubscription subscriptionId = recorderEndpoint
          .addStoppedListener(new EventListener<StoppedEvent>() {

            @Override
            public void onEvent(StoppedEvent event) {
              stoppedCountDown.countDown();
            }
          });
      recorderEndpoint.stop();
      try {
        if (!stoppedCountDown.await(5, TimeUnit.SECONDS)) {
          log.error("Error waiting for recorder to stop");
        }
      } catch (InterruptedException e) {
        log.error("Exception while waiting for state change", e);
      }
      recorderEndpoint.removeStoppedListener(subscriptionId);
      recorderEndpoint.release();
    }
    mediaPipeline.release();
  }

  public void release() {
    this.mediaPipeline.release();
    this.webRtcEndpoint = null;
    // this.webRtcEndpointAudio = null;
    this.webRtcEndpointVideo = null;
    this.mediaPipeline = null;
    if (this.stopTimestamp == null) {
      this.stopTimestamp = new Date();
    }
  }

  
  //shj

  public void pause(){
    try {
      if(recorderEndpoint != null){
        recorderEndpoint.pause();
      }
    } catch (Exception e) {
      log.error("Paused Error");
      e.printStackTrace();
    }
  }

  public void resume(){
    try {
      if(recorderEndpoint != null){
        recorderEndpoint.record();
      }
    } catch (Exception e) {
      log.error("Resumed Error");
      e.printStackTrace();
    }
  }

  public void stopNwait(){
    if (recorderEndpoint != null) {
      final CountDownLatch stoppedCountDown = new CountDownLatch(1);
      ListenerSubscription subscriptionId = recorderEndpoint
          .addStoppedListener(new EventListener<StoppedEvent>() {

            @Override
            public void onEvent(StoppedEvent event) {
              stoppedCountDown.countDown();
            }
          });
      recorderEndpoint.stopAndWait();
      log.info("recorderEP stop and wait...");
      try {
        if (!stoppedCountDown.await(5, TimeUnit.SECONDS)) {
          log.error("Error waiting for recorder to stop");
        }
      } catch (InterruptedException e) {
        log.error("Exception while waiting for state change", e);
      }
      recorderEndpoint.removeStoppedListener(subscriptionId);
      recorderEndpoint.release();
      recorderEndpoint = null;

      // try {
        // nettyWebSocket.closeNativeClient();
        // nettyWebSocket.close();
      // } catch (IOException e) {
      //   e.printStackTrace();
      // }
    }
    mediaPipeline.release();
  }

  private WebRtcEndpoint plusEndPoint;
  private HubPort inHubPort;

  public void plus(final WebSocketSession session, JsonObject jsonMessage){
    try{

      if(plusEndPoint != null){
        plusEndPoint.disconnect(inHubPort, MediaType.AUDIO);
        plusEndPoint.disconnect(inHubPort, MediaType.VIDEO);
        inHubPort.release();
        inHubPort = null;
        plusEndPoint.release();
        plusEndPoint = null;
        return;
      }

      plusEndPoint = new WebRtcEndpoint.Builder(mediaPipeline).build();
      inHubPort = new HubPort.Builder(composite).build();

      plusEndPoint.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent ev) {
          log.error("[WebRtcEndpoint::ErrorEvent2] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
              ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
              ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
            sendError(session, "[WebRtcEndpoint] " + ev.getDescription());
        }
      });
      inHubPort.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent ev) {
          log.error("[WebRtcEndpoint::ErrorEvent2] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
              ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
              ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
            sendError(session, "[WebRtcEndpoint] " + ev.getDescription());
        }
      });
      plusEndPoint.addMediaFlowOutStateChangeListener(new EventListener<MediaFlowOutStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowOutStateChangeEvent event) {
          log.info("[plus webRtcEP Media Flow Out State Changed Event] ::: Media Flow Out State Changed [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowOutStateChanged");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });
      inHubPort.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowInStateChangeEvent event) {
          log.info("[plus inHubPort Media Flow In State Changed Event] ::: Media Flow In State Changed [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      plusEndPoint.connect(inHubPort, MediaType.AUDIO);
      plusEndPoint.connect(inHubPort, MediaType.VIDEO);

      String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
      String sdpAnswer = plusEndPoint.processOffer(sdpOffer);

      plusEndPoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

        @Override
        public void onEvent(IceCandidateFoundEvent event) {
          log.info("[plus Ice Candidate Found Event] ::: Ice Candidate Founded");
          JsonObject response = new JsonObject();
          response.addProperty("id", "iceCandidatePlus");
          response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }
      });

      JsonObject response = new JsonObject();
      response.addProperty("id", "plusResponse");
      response.addProperty("sdpAnswer", sdpAnswer);

      synchronized (this) {
        session.sendMessage(new TextMessage(response.toString()));
      }

      plusEndPoint.gatherCandidates();

    }catch(Throwable t){
      log.error("Composite Start error", t);
      sendError(session, t.getMessage());
    }
  }

  private void sendError(WebSocketSession session, String message) {
    JsonObject response = new JsonObject();
    response.addProperty("id", "error");
    response.addProperty("message", message);

    try {
      synchronized (session) {
        session.sendMessage(new TextMessage(response.toString()));
      }
    } catch (IOException e) {
      log.error("Exception sending message", e);
    }
  }
}