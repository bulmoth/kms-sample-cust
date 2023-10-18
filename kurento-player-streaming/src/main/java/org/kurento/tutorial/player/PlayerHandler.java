/*
 * (C) Copyright 2015 Kurento (http://kurento.org/)
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
 *
 */

package org.kurento.tutorial.player;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaFlowInStateChangedEvent;
import org.kurento.client.MediaFlowOutStateChangedEvent;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaState;
import org.kurento.client.MediaStateChangedEvent;
import org.kurento.client.MediaType;
import org.kurento.client.PlayerEndpoint;
import org.kurento.client.RtpEndpoint;
import org.kurento.client.ServerManager;
import org.kurento.client.VideoInfo;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.commons.exception.KurentoException;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Protocol handler for video player through WebRTC.
 *
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @author David Fernandez (dfernandezlop@gmail.com)
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 6.1.1
 */
public class PlayerHandler extends TextWebSocketHandler {

  @Autowired
  private KurentoClient kurento;

  private final Logger log = LoggerFactory.getLogger(PlayerHandler.class);
  private final Gson gson = new GsonBuilder().create();
  private final ConcurrentHashMap<String, UserSession> users = new ConcurrentHashMap<>();

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
    String sessionId = session.getId();
    log.debug("Incoming message {} from sessionId", jsonMessage, sessionId);

    try {
      switch (jsonMessage.get("id").getAsString()) {
        case "start":
          start(session, jsonMessage);
          break;
        case "stop":
          stop(sessionId);
          break;
        case "pause":
          pause(sessionId);
          break;
        case "resume":
          resume(session);
          break;
        case "debugDot":
          debugDot(session);
          break;
        case "doSeek":
          doSeek(session, jsonMessage);
          break;
        case "getPosition":
          getPosition(session);
          break;
        case "onIceCandidate":
          onIceCandidate(sessionId, jsonMessage);
          break;
        case "createSdp":
          createSdp(session, jsonMessage);
          break;
        default:
          sendError(session, "Invalid message with id " + jsonMessage.get("id").getAsString());
          break;
      }
    } catch (Throwable t) {
      log.error("Exception handling message {} in sessionId {}", jsonMessage, sessionId, t);
      sendError(session, t.getMessage());
    }
  }

  private void createSdp(WebSocketSession session, JsonObject jsonMessage){
    final UserSession user = new UserSession();
    users.put(session.getId(), user);
    MediaPipeline pipeline = kurento.createMediaPipeline();
    user.setMediaPipeline(pipeline);

    RtpEndpoint rtpEndpoint = new RtpEndpoint.Builder(pipeline).build();
    user.setRtpEndpoint(rtpEndpoint);
    // String dummySdpOffer = rtpEndpoint.generateOffer();

    String rtpSdpOffer = "v=0\r\n" + //
        "o=- 0 0 IN IP4 127.0.0.1\r\n" + //
        "s=No Name\r\n" + //
        "c=IN IP4 192.168.0.218\r\n" + //
        "t=0 0\r\n" + //
        "a=tool:libavformat 60.9.100\r\n" + //
        "m=video 30556 RTP/AVP 96\r\n" + //
        "a=rtpmap:96 H264/90000\r\n" + //
        "a=fmtp:96 packetization-mode=1";

    String sdpAnswer = rtpEndpoint.processOffer(rtpSdpOffer);

    // String rtpSdpAnswer = rtpEndpoint.processAnswer(rtpSdpOffer);

    // log.info("[Handler::createSdp] dummy rtpSDP Offer from browser to KMS:\n{}", dummySdpOffer);
    log.info("[Handler::createSdp] dummy rtpSDP Offer from browser to KMS:\n{}", sdpAnswer);

    rtpEndpoint.addMediaFlowOutStateChangedListener(new EventListener<MediaFlowOutStateChangedEvent>() {
      @Override
      public void onEvent(MediaFlowOutStateChangedEvent event) {
        log.info("rtp Media Flow Out State Change :: [{}]", event.getState());
      }
    });
    rtpEndpoint.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
      public void onEvent(MediaFlowInStateChangedEvent event) {
        log.info("rtp Media Flow In State Change :: [{}]", event.getState());
      }
    });
  }

  private void start(final WebSocketSession session, JsonObject jsonMessage) {
    // 1. Media pipeline
    UserSession user = users.get(session.getId());
    MediaPipeline pipeline = user.getMediaPipeline();
    
    WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
    user.setWebRtcEndpoint(webRtcEndpoint);
    
    // String rtpSdpOffer = jsonMessage.get("rtpSdpOffer").getAsString();
    RtpEndpoint rtpEndpoint = user.getRtpEndpoint();
    // String rtpSdpAnswer = rtpEndpoint.processAnswer(rtpSdpOffer);

    // String rtpSdpAnswer = rtpEndpoint.processOffer(rtpSdpOffer);

    // log.info("[Handler::start] rtpSDP Offer from browser to KMS:\n{}", rtpSdpOffer);
    // log.info("[Handler::start] rtpSDP Answer from KMS to browser:\n{}", rtpSdpAnswer);

    // 2. WebRtcEndpoint
    // ICE candidates
    webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {
      @Override
      public void onEvent(IceCandidateFoundEvent event) {
        JsonObject response = new JsonObject();
        response.addProperty("id", "iceCandidate");
        response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
        try {
          synchronized (session) {
            session.sendMessage(new TextMessage(response.toString()));
          }
        } catch (IOException e) {
          log.debug(e.getMessage());
        }
      }
    });

    webRtcEndpoint.addMediaFlowOutStateChangedListener(new EventListener<MediaFlowOutStateChangedEvent>() {
      @Override
      public void onEvent(MediaFlowOutStateChangedEvent event) {
        log.info("webRtc Media Flow Out State Change :: [{}]", event.getState());
      }
    });
     webRtcEndpoint.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
      public void onEvent(MediaFlowInStateChangedEvent event) {
        log.info("webRtc Media Flow In State Change :: [{}]", event.getState());
      }
    });

    // Continue the SDP Negotiation: Generate an SDP Answer
    String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
    String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);

    log.info("[Handler::start] SDP Offer from browser to KMS:\n{}", sdpOffer);
    log.info("[Handler::start] SDP Answer from KMS to browser:\n{}", sdpAnswer);

    JsonObject response = new JsonObject();
    response.addProperty("id", "startResponse");
    response.addProperty("sdpAnswer", sdpAnswer);
    sendMessage(session, response.toString());
    
    rtpEndpoint.connect(webRtcEndpoint);

    webRtcEndpoint.gatherCandidates();
  }

  private void pause(String sessionId) {
    UserSession user = users.get(sessionId);

    if (user != null) {
      user.getPlayerEndpoint().pause();
    }
  }

  private void resume(final WebSocketSession session) {
    UserSession user = users.get(session.getId());

    if (user != null) {
      user.getPlayerEndpoint().play();
      VideoInfo videoInfo = user.getPlayerEndpoint().getVideoInfo();

      JsonObject response = new JsonObject();
      response.addProperty("id", "videoInfo");
      response.addProperty("isSeekable", videoInfo.getIsSeekable());
      response.addProperty("initSeekable", videoInfo.getSeekableInit());
      response.addProperty("endSeekable", videoInfo.getSeekableEnd());
      response.addProperty("videoDuration", videoInfo.getDuration());
      sendMessage(session, response.toString());
    }
  }

  private void stop(String sessionId) {
    UserSession user = users.remove(sessionId);

    if (user != null) {
      user.release();
    }
  }

  private void debugDot(final WebSocketSession session) {
    UserSession user = users.get(session.getId());

    if (user != null) {
      final String pipelineDot = user.getMediaPipeline().getGstreamerDot();
      try (PrintWriter out = new PrintWriter("player.dot")) {
        out.println(pipelineDot);
      } catch (IOException ex) {
        log.error("[Handler::debugDot] Exception: {}", ex.getMessage());
      }
      final String playerDot = user.getPlayerEndpoint().getElementGstreamerDot();
      try (PrintWriter out = new PrintWriter("player-decoder.dot")) {
        out.println(playerDot);
      } catch (IOException ex) {
        log.error("[Handler::debugDot] Exception: {}", ex.getMessage());
      }
    }

    ServerManager sm = kurento.getServerManager();
    log.warn("[Handler::debugDot] CPU COUNT: {}", sm.getCpuCount());
    log.warn("[Handler::debugDot] CPU USAGE: {}", sm.getUsedCpu(1000));
    log.warn("[Handler::debugDot] RAM USAGE: {}", sm.getUsedMemory());
  }

  private void doSeek(final WebSocketSession session, JsonObject jsonMessage) {
    UserSession user = users.get(session.getId());

    if (user != null) {
      try {
        user.getPlayerEndpoint().setPosition(jsonMessage.get("position").getAsLong());
      } catch (KurentoException e) {
        log.debug("The seek cannot be performed");
        JsonObject response = new JsonObject();
        response.addProperty("id", "seek");
        response.addProperty("message", "Seek failed");
        sendMessage(session, response.toString());
      }
    }
  }

  private void getPosition(final WebSocketSession session) {
    UserSession user = users.get(session.getId());

    if (user != null) {
      long position = user.getPlayerEndpoint().getPosition();

      JsonObject response = new JsonObject();
      response.addProperty("id", "position");
      response.addProperty("position", position);
      sendMessage(session, response.toString());
    }
  }

  private void onIceCandidate(String sessionId, JsonObject jsonMessage) {
    UserSession user = users.get(sessionId);

    if (user != null) {
      JsonObject jsonCandidate = jsonMessage.get("candidate").getAsJsonObject();
      IceCandidate candidate =
          new IceCandidate(jsonCandidate.get("candidate").getAsString(), jsonCandidate
              .get("sdpMid").getAsString(), jsonCandidate.get("sdpMLineIndex").getAsInt());
      user.getWebRtcEndpoint().addIceCandidate(candidate);
    }
  }

  public void sendPlayEnd(WebSocketSession session) {
    if (users.containsKey(session.getId())) {
      JsonObject response = new JsonObject();
      response.addProperty("id", "playEnd");
      sendMessage(session, response.toString());
    }
  }

  private void sendError(WebSocketSession session, String message) {
    if (users.containsKey(session.getId())) {
      JsonObject response = new JsonObject();
      response.addProperty("id", "error");
      response.addProperty("message", message);
      sendMessage(session, response.toString());
    }
  }

  private synchronized void sendMessage(WebSocketSession session, String message) {
    try {
      session.sendMessage(new TextMessage(message));
    } catch (IOException e) {
      log.error("Exception sending message", e);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    stop(session.getId());
  }
}
