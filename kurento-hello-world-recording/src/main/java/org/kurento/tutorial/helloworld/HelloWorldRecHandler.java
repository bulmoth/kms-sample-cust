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
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.ws.WebEndpoint;

import org.kurento.client.Composite;
import org.kurento.client.ElementConnectedEvent;
import org.kurento.client.ElementDisconnectedEvent;
import org.kurento.client.EndOfStreamEvent;
import org.kurento.client.Endpoint;
import org.kurento.client.ErrorEvent;
import org.kurento.client.EventListener;
import org.kurento.client.HubPort;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaFlowInStateChangeEvent;
import org.kurento.client.MediaFlowOutStateChangeEvent;
import org.kurento.client.MediaFlowState;
import org.kurento.client.MediaObject;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaProfileSpecType;
import org.kurento.client.MediaTranscodingStateChangeEvent;
import org.kurento.client.MediaType;
import org.kurento.client.PausedEvent;
import org.kurento.client.PlayerEndpoint;
import org.kurento.client.Properties;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.RecordingEvent;
import org.kurento.client.StoppedEvent;
import org.kurento.client.UriEndpointStateChangedEvent;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.client.internal.client.RomClient;
import org.kurento.client.internal.client.RomManager;
import org.kurento.client.internal.transport.serialization.ParamsFlattener.RomType;
import org.kurento.jsonrpc.JsonUtils;
import org.kurento.jsonrpc.client.JsonRpcClientNettyWebSocket;
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
 * Hello World with recording handler (application and media logic).
 *
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @author David Fernandez (d.fernandezlop@gmail.com)
 * @author Radu Tom Vlad (rvlad@naevatec.com)
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 6.1.1
 */
public class HelloWorldRecHandler extends TextWebSocketHandler {

  private static Date date = new Date();
  private static SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
  private static String dateStr = format.format(date);
  private static final String RECORDER_FILE_PATH = "file:///tmp/HelloWorldRecorded_" + dateStr + ".webm";
  //private static final String RECORDER_FILE_PATH = "file:///tmp/HelloWorldRecorded_" + dateStr + ".mp4";
  private static final String AUDIO_FILE_PATH = "file:///tmp/HelloWorldRecorded_" + dateStr + ".mp3";

  private final Logger log = LoggerFactory.getLogger(HelloWorldRecHandler.class);
  private static final Gson gson = new GsonBuilder().create();

  private static UserSession userLocal = null;

  //private static final JsonRpcClientNettyWebSocket nettyWebSocket = new JsonRpcClientNettyWebSocket("ws://192.168.0.218:8888/kurento");


  @Autowired
  private UserRegistry registry;

  @Autowired
  private KurentoClient kurento;

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);

    log.debug("Incoming message: {}", jsonMessage);

    UserSession user = registry.getBySession(session);
    if (user != null) {
      log.debug("Incoming message from user '{}': {}", user.getId(), jsonMessage);
    } else {
      log.debug("Incoming message from new user: {}", jsonMessage);
    }

    String recordMode = jsonMessage.get("recordMode").getAsString();

    switch (jsonMessage.get("id").getAsString()) {
      case "start":
        if("agent".equals(recordMode)){
          start(session, jsonMessage);
        }else{
          log.info("session :  {}", session);
          startComposite(session, jsonMessage);
        }
        break;
      case "stop":
        if (user != null) {
          user.stop();
        }
      case "stopPlay":
        if (user != null) {
          registry.getById("start").getMediaPipeline().release();
          user.release();
          userLocal.stopNwait();
          userLocal.getMediaPipeline().release();
          userLocal.release();
        }
        break;
      case "play":
        play(user, session, jsonMessage);
        break;

      case "onIceCandidate": {
        JsonObject jsonCandidate = jsonMessage.get("candidate").getAsJsonObject();

        if (user != null) {
          IceCandidate candidate = new IceCandidate(jsonCandidate.get("candidate").getAsString(),
              jsonCandidate.get("sdpMid").getAsString(),
              jsonCandidate.get("sdpMLineIndex").getAsInt());
          user.addCandidate(candidate);
        }
        break;
      }
      
      //shj

      case "plus":
        log.info("session :  {}", session);
        user = registry.getById("start");
        user.plus(session, jsonMessage);
        break;

      case "stopNwait":
      if(user != null){
        user = registry.getById("start");
        user.stopNwait();
        registry.getById("start").getMediaPipeline().release();
        user.release();
        userLocal.stopNwait();
        userLocal.getMediaPipeline().release();
        userLocal.release();
      }
        break;

      case "disconnect":
        disconnect(session);
        break;

      case "pause":
      user = registry.getById("start");
      if(user != null){
        log.info("user is not null...");
        user.pause();
      }
      break;
    case "resume":
    user = registry.getById("start");
    if(user != null){
      user.resume();
    }
      break;
      default:
        sendError(session, "Invalid message with id " + jsonMessage.get("id").getAsString());
        break;
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    super.afterConnectionClosed(session, status);
    registry.removeBySession(session);
  }

  private void start(final WebSocketSession session, JsonObject jsonMessage) {
    try {

      // 1. Media logic (webRtcEndpoint in loopback)
      MediaPipeline pipeline = kurento.createMediaPipeline();
      WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
      // WebRtcEndpoint webRtcEndpointAudio = new WebRtcEndpoint.Builder(pipeline).build();
      // WebRtcEndpoint webRtcEndpointVideo = new WebRtcEndpoint.Builder(pipeline).build();

      int min = 1500;
      int max = 2500;

      webRtcEndpoint.setMinVideoSendBandwidth(min);
      webRtcEndpoint.setMaxVideoSendBandwidth(max);
      webRtcEndpoint.setMinVideoRecvBandwidth(min);
      webRtcEndpoint.setMaxVideoRecvBandwidth(max);
      webRtcEndpoint.setMinOuputBitrate(min*1024);
      webRtcEndpoint.setMaxOuputBitrate(max*1024);

      webRtcEndpoint.connect(webRtcEndpoint);
      // webRtcEndpointAudio.connect(webRtcEndpointAudio);
      // webRtcEndpointVideo.connect(webRtcEndpointVideo);

      MediaProfileSpecType profile = getMediaProfileFromMessage(jsonMessage);

      RecorderEndpoint recorder = new RecorderEndpoint.Builder(pipeline, RECORDER_FILE_PATH)
      .withMediaProfile(profile).build();

      recorder.setMinOuputBitrate(min*1024);
      recorder.setMaxOuputBitrate(max*1024);

      // Error listeners.
      pipeline.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent ev) {
          log.error("[MediaPipeline::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
              ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
              ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
            sendError(session, "[MediaPipeline] " + ev.getDescription());
        }
      });
      webRtcEndpoint.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent ev) {
          log.error("[WebRtcEndpoint::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
              ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
              ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
            sendError(session, "[WebRtcEndpoint] " + ev.getDescription());
        }
      });
      // webRtcEndpointAudio.addErrorListener(new EventListener<ErrorEvent>() {
      //   @Override
      //   public void onEvent(ErrorEvent ev) {
      //     log.error("[WebRtcEndpointAudio::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
      //         ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
      //         ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
      //       sendError(session, "[WebRtcEndpoint] " + ev.getDescription());
      //   }
      // });
      // webRtcEndpointVideo.addErrorListener(new EventListener<ErrorEvent>() {
      //   @Override
      //   public void onEvent(ErrorEvent ev) {
      //     log.error("[WebRtcEndpointVideo::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
      //         ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
      //         ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
      //       sendError(session, "[WebRtcEndpoint] " + ev.getDescription());
      //   }
      // });
      recorder.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent ev) {
          log.error("[RecorderEndpoint::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
              ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
              ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
            sendError(session, "[RecorderEndpoint] " + ev.getDescription());
        }
      });

      recorder.addRecordingListener(new EventListener<RecordingEvent>() {

        @Override
        public void onEvent(RecordingEvent event) {
          log.info("[Agent Recording Event] ::: recording");
          JsonObject response = new JsonObject();
          response.addProperty("id", "recording");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recorder.addStoppedListener(new EventListener<StoppedEvent>() {

        @Override
        public void onEvent(StoppedEvent event) {
          log.info("[Agent Recording Stoppend Event] ::: stopped");
          JsonObject response = new JsonObject();
          response.addProperty("id", "stopped");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recorder.addPausedListener(new EventListener<PausedEvent>() {

        @Override
        public void onEvent(PausedEvent event) {

          log.info("[Agent Recording Paused Event] ::: paused");
          JsonObject response = new JsonObject();
          response.addProperty("id", "paused");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recorder.addElementConnectedListener(new EventListener<ElementConnectedEvent>() {

        @Override
        public void onEvent(ElementConnectedEvent event) {
          log.info("[Agent Recording Element Connected Event] ::: element Connected");
          JsonObject response = new JsonObject();
          response.addProperty("id", "elementConnected");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recorder.addElementDisconnectedListener(new EventListener<ElementDisconnectedEvent>() {

        @Override
        public void onEvent(ElementDisconnectedEvent event) {
          log.info("[Agent Recording Element Disconnected Event] ::: element Disconnected");
          JsonObject response = new JsonObject();
          response.addProperty("id", "elementDisconnected");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recorder.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowInStateChangeEvent event) {
          log.info("[Agent Recording Media Flow In State Changed Event] ::: Media Flow In State Changed [{}]", event.getState());
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

      webRtcEndpoint.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowInStateChangeEvent event) {
          log.info("[Agent webRtcEP Media Flow In State Changed Event] ::: Media Flow In State Changed [{}]", event.getState());
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

      webRtcEndpoint.addMediaFlowOutStateChangeListener(new EventListener<MediaFlowOutStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowOutStateChangeEvent event) {
          log.info("[Agent webRtcEP Media Flow Out State Changed Event] ::: Media Flow Out State Changed [{}]", event.getState());
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
      // webRtcEndpointAudio.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {

      //   @Override
      //   public void onEvent(MediaFlowInStateChangeEvent event) {
      //     log.info("[Agent webRtcEP Media Flow In State Changed Event] ::: Media Flow In State Changed [{}]", event.getState());
      //     JsonObject response = new JsonObject();
      //     response.addProperty("id", "mediaFlowInStateChanged");
      //     try {
      //       synchronized (session) {
      //         session.sendMessage(new TextMessage(response.toString()));
      //       }
      //     } catch (IOException e) {
      //       log.error(e.getMessage());
      //     }
      //   }

      // });

      // webRtcEndpointAudio.addMediaFlowOutStateChangeListener(new EventListener<MediaFlowOutStateChangeEvent>() {

      //   @Override
      //   public void onEvent(MediaFlowOutStateChangeEvent event) {
      //     log.info("[Agent webRtcAudioEP Media Flow Out State Changed Event] ::: Media Flow Out State Changed [{}]", event.getState());
      //     JsonObject response = new JsonObject();
      //     response.addProperty("id", "mediaFlowOutStateChanged");
      //     try {
      //       synchronized (session) {
      //         session.sendMessage(new TextMessage(response.toString()));
      //       }
      //     } catch (IOException e) {
      //       log.error(e.getMessage());
      //     }
      //   }

      // });
      // webRtcEndpointVideo.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {

      //   @Override
      //   public void onEvent(MediaFlowInStateChangeEvent event) {
      //     log.info("[Agent webRtcVideoEP Media Flow In State Changed Event] ::: Media Flow In State Changed [{}]", event.getState());
      //     JsonObject response = new JsonObject();
      //     response.addProperty("id", "mediaFlowInStateChanged");
      //     try {
      //       synchronized (session) {
      //         session.sendMessage(new TextMessage(response.toString()));
      //       }
      //     } catch (IOException e) {
      //       log.error(e.getMessage());
      //     }
      //   }

      // });

      // webRtcEndpointVideo.addMediaFlowOutStateChangeListener(new EventListener<MediaFlowOutStateChangeEvent>() {

      //   @Override
      //   public void onEvent(MediaFlowOutStateChangeEvent event) {
      //     log.info("[Agent webRtcEP Media Flow Out State Changed Event] ::: Media Flow Out State Changed [{}]", event.getState());
      //     JsonObject response = new JsonObject();
      //     response.addProperty("id", "mediaFlowOutStateChanged");
      //     try {
      //       synchronized (session) {
      //         session.sendMessage(new TextMessage(response.toString()));
      //       }
      //     } catch (IOException e) {
      //       log.error(e.getMessage());
      //     }
      //   }

      // });

      recorder.addUriEndpointStateChangedListener(new EventListener<UriEndpointStateChangedEvent>() {

        @Override
        public void onEvent(UriEndpointStateChangedEvent event) {
          log.info("[Agent Recording Uri Endpoint State Changed Event] ::: Uri Endpoint State Changed [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "uriEndpointStateChanged");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recorder.addMediaTranscodingStateChangeListener(new EventListener<MediaTranscodingStateChangeEvent>() {

        @Override
        public void onEvent(MediaTranscodingStateChangeEvent event) {
          log.info("[Agent Recording Media Transcoding State Changed Event] ::: Media Transcoding State Changed [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaTranscodingStateChanged");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      connectAccordingToProfile(webRtcEndpoint, recorder, profile);
      // connectAccordingToProfile(webRtcEndpointAudio, recorder, MediaProfileSpecType.WEBM_AUDIO_ONLY);
      // connectAccordingToProfile(webRtcEndpointAudio, recorder, MediaProfileSpecType.WEBM_VIDEO_ONLY);

      // 2. Store user session
      UserSession user = new UserSession(session);
      user.setMediaPipeline(pipeline);
      user.setWebRtcEndpoint(webRtcEndpoint);
      // user.setWebRtcEndpointAudio(webRtcEndpointAudio);
      // user.setWebRtcEndpointVideo(webRtcEndpointVideo);
      user.setRecorderEndpoint(recorder);
      registry.register(user);

      // 3. SDP negotiation
      String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
      // String sdpOfferAudio = jsonMessage.get("sdpOfferAudio").getAsString();
      // String sdpOfferVideo = jsonMessage.get("sdpOfferVideo").getAsString();
      String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);
      // String sdpAnswerAudio = webRtcEndpointAudio.processOffer(sdpOfferAudio);
      // String sdpAnswerVideo = webRtcEndpointVideo.processOffer(sdpOfferVideo);

      // 4. Gather ICE candidates
      webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

        @Override
        public void onEvent(IceCandidateFoundEvent event) {
          log.info("[Agent Ice Candidate Found Event] ::: Ice Candidate Founded");
          JsonObject response = new JsonObject();
          response.addProperty("id", "iceCandidate");
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

      // webRtcEndpointAudio.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

      //   @Override
      //   public void onEvent(IceCandidateFoundEvent event) {
      //     log.info("[Agent Audio Ice Candidate Found Event] ::: Ice Candidate Founded");
      //     JsonObject response = new JsonObject();
      //     response.addProperty("id", "iceCandidate");
      //     response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
      //     try {
      //       synchronized (session) {
      //         session.sendMessage(new TextMessage(response.toString()));
      //       }
      //     } catch (IOException e) {
      //       log.error(e.getMessage());
      //     }
      //   }
      // });

      // webRtcEndpointVideo.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

      //   @Override
      //   public void onEvent(IceCandidateFoundEvent event) {
      //     log.info("[Agent Video Ice Candidate Found Event] ::: Ice Candidate Founded");
      //     JsonObject response = new JsonObject();
      //     response.addProperty("id", "iceCandidate");
      //     response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
      //     try {
      //       synchronized (session) {
      //         session.sendMessage(new TextMessage(response.toString()));
      //       }
      //     } catch (IOException e) {
      //       log.error(e.getMessage());
      //     }
      //   }
      // });

      JsonObject response = new JsonObject();
      response.addProperty("id", "startResponse");
      response.addProperty("sdpAnswer", sdpAnswer);
      // response.addProperty("sdpAnswerAudio", sdpAnswerAudio);
      // response.addProperty("sdpAnswerVideo", sdpAnswerVideo);

      synchronized (user) {
        session.sendMessage(new TextMessage(response.toString()));
      }

      webRtcEndpoint.gatherCandidates();
      // webRtcEndpointAudio.gatherCandidates();
      // webRtcEndpointVideo.gatherCandidates();

      recorder.record();
    } catch (Throwable t) {
      log.error("Start error", t);
      sendError(session, t.getMessage());
    }
  }

  private MediaProfileSpecType getMediaProfileFromMessage(JsonObject jsonMessage) {

    MediaProfileSpecType profile;
    switch (jsonMessage.get("mode").getAsString()) {
      case "audio-only":
        profile = MediaProfileSpecType.WEBM_AUDIO_ONLY;
        break;
      case "video-only":
        profile = MediaProfileSpecType.WEBM_VIDEO_ONLY;
        break;
      default:
        profile = MediaProfileSpecType.WEBM;
    }

    return profile;
  }

  private void connectAccordingToProfile(WebRtcEndpoint webRtcEndpoint, RecorderEndpoint recorder,
      MediaProfileSpecType profile) {
    switch (profile) {
      case WEBM:
        webRtcEndpoint.connect(recorder, MediaType.AUDIO);
        webRtcEndpoint.connect(recorder, MediaType.VIDEO);
        break;
      case WEBM_AUDIO_ONLY:
        webRtcEndpoint.connect(recorder, MediaType.AUDIO);
        break;
      case WEBM_VIDEO_ONLY:
        webRtcEndpoint.connect(recorder, MediaType.VIDEO);
        break;
      default:
        throw new UnsupportedOperationException("Unsupported profile for this tutorial: " + profile);
    }
  }

  private void play(UserSession user, final WebSocketSession session, JsonObject jsonMessage) {
    try {

      // 1. Media logic
      final MediaPipeline pipeline = kurento.createMediaPipeline();
      WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
      PlayerEndpoint player = new PlayerEndpoint.Builder(pipeline, RECORDER_FILE_PATH).build();
      player.connect(webRtcEndpoint);

      // Player listeners
      player.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent event) {
          log.info("ErrorEvent for session '{}': {}", session.getId(), event.getDescription());
          sendPlayEnd(session, pipeline);
        }
      });
      player.addEndOfStreamListener(new EventListener<EndOfStreamEvent>() {
        @Override
        public void onEvent(EndOfStreamEvent event) {
          log.info("EndOfStreamEvent for session '{}'", session.getId());
          sendPlayEnd(session, pipeline);
        }
      });

      // 2. Store user session
      user.setMediaPipeline(pipeline);
      user.setWebRtcEndpoint(webRtcEndpoint);

      // 3. SDP negotiation
      String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
      String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);

      JsonObject response = new JsonObject();
      response.addProperty("id", "playResponse");
      response.addProperty("sdpAnswer", sdpAnswer);

      // 4. Gather ICE candidates
      webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

        @Override
        public void onEvent(IceCandidateFoundEvent event) {
          log.info("[Playing Ice Candidate Found Event] ::: Ice Candidate Founded");
          JsonObject response = new JsonObject();
          response.addProperty("id", "iceCandidate");
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

      // 5. Play recorded stream
      player.play();

      synchronized (session) {
        session.sendMessage(new TextMessage(response.toString()));
      }

      webRtcEndpoint.gatherCandidates();
    } catch (Throwable t) {
      log.error("Play error", t);
      sendError(session, t.getMessage());
    }
  }

  public void sendPlayEnd(WebSocketSession session, MediaPipeline pipeline) {
    try {
      JsonObject response = new JsonObject();
      response.addProperty("id", "playEnd");
      session.sendMessage(new TextMessage(response.toString()));
    } catch (IOException e) {
      log.error("Error sending playEndOfStream message", e);
    }
    // Release pipeline
    pipeline.release();
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


  private void startComposite(final WebSocketSession session, JsonObject jsonMessage){
    try{

      MediaPipeline pipeline = kurento.createMediaPipeline();
      Composite composite = new Composite.Builder(pipeline).build();
      WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();

      int min = 1500;
      int max = 2500;

      webRtcEndpoint.setMinVideoSendBandwidth(min);
      webRtcEndpoint.setMaxVideoSendBandwidth(max);
      webRtcEndpoint.setMinVideoRecvBandwidth(min);
      webRtcEndpoint.setMaxVideoRecvBandwidth(max);
      webRtcEndpoint.setMinOuputBitrate(min*1024);
      webRtcEndpoint.setMaxOuputBitrate(max*1024);
      //webRtcEndpoint.setOutputBitrate(middle);

      HubPort recHubPort = new HubPort.Builder(composite).build();
      HubPort inHubPort = new HubPort.Builder(composite).build();
      HubPort outHubPort = new HubPort.Builder(composite).build();
      RecorderEndpoint recorder = new RecorderEndpoint.Builder(pipeline, RECORDER_FILE_PATH).build();
      RecorderEndpoint recorderAudio = new RecorderEndpoint.Builder(pipeline, AUDIO_FILE_PATH).withMediaProfile(MediaProfileSpecType.MP4_AUDIO_ONLY).build();

      recorder.setMinOuputBitrate(min*1024);
      recorder.setMaxOuputBitrate(max*1024);

      MediaProfileSpecType profile = getMediaProfileFromMessage(jsonMessage);

      //add Error Listener
      pipeline.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent ev) {
          log.error("[MediaPipeline::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
              ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
              ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
            sendError(session, "[MediaPipeline] " + ev.getDescription());
        }
      });
      webRtcEndpoint.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent ev) {
          log.error("[WebRtcEndpoint::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
              ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
              ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
            sendError(session, "[WebRtcEndpoint] " + ev.getDescription());
        }
      });
      recorder.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent ev) {
          log.error("[RecorderEndpoint::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
              ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
              ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
            sendError(session, "[RecorderEndpoint] " + ev.getDescription());
        }
      });
      recorderAudio.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent ev) {
          log.error("[RecorderEndpoint::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
              ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
              ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
            sendError(session, "[RecorderEndpoint] " + ev.getDescription());
        }
      });

      inHubPort.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowInStateChangeEvent event) {
          log.info("[Composite inHubPort Media Flow In State Changed Event] ::: Media Flow In State Changed [{}]", event.getState());
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

      inHubPort.addMediaFlowOutStateChangeListener(new EventListener<MediaFlowOutStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowOutStateChangeEvent event) {
          log.info("[Composite inHubPort Media Flow Out State Changed Event] ::: Media Flow Out State Changed [{}]", event.getState());
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

      outHubPort.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowInStateChangeEvent event) {
          log.info("[Composite outHubPort Media Flow In State Changed Event] ::: Media Flow In State Changed [{}]", event.getState());
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

      outHubPort.addMediaFlowOutStateChangeListener(new EventListener<MediaFlowOutStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowOutStateChangeEvent event) {
          log.info("[Composite outHubPort Media Flow Out State Changed Event] ::: Media Flow Out State Changed [{}]", event.getState());
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

      recHubPort.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowInStateChangeEvent event) {
          log.info("[Composite recHubPort Media Flow In State Changed Event] ::: Media Flow In State Changed [{}]", event.getState());
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

      recHubPort.addMediaFlowOutStateChangeListener(new EventListener<MediaFlowOutStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowOutStateChangeEvent event) {
          log.info("[Composite recHubPort Media Flow Out State Changed Event] ::: Media Flow Out State Changed [{}]", event.getState());
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

      recorder.addRecordingListener(new EventListener<RecordingEvent>() {

        @Override
        public void onEvent(RecordingEvent event) {
          log.info("[Composite Recording Event] ::: recording");
          JsonObject response = new JsonObject();
          response.addProperty("id", "recording");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recorder.addStoppedListener(new EventListener<StoppedEvent>() {

        @Override
        public void onEvent(StoppedEvent event) {
          log.info("[Composite Recording Stoppend Event] ::: stopped");
          JsonObject response = new JsonObject();
          response.addProperty("id", "stopped");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recorder.addPausedListener(new EventListener<PausedEvent>() {

        @Override
        public void onEvent(PausedEvent event) {
          log.info("[Composite Recording Paused Event] ::: paused");
          JsonObject response = new JsonObject();
          response.addProperty("id", "paused");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recorder.addElementConnectedListener(new EventListener<ElementConnectedEvent>() {

        @Override
        public void onEvent(ElementConnectedEvent event) {
          log.info("[Composite Recording Element Connected Event] ::: element Connected");
          JsonObject response = new JsonObject();
          response.addProperty("id", "elementConnected");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recorder.addElementDisconnectedListener(new EventListener<ElementDisconnectedEvent>() {

        @Override
        public void onEvent(ElementDisconnectedEvent event) {
          log.info("[Composite Recording Element Disconnected Event] ::: element Disconnected");
          JsonObject response = new JsonObject();
          response.addProperty("id", "elementDisconnected");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recorder.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowInStateChangeEvent event) {
          log.info("[Composite Recording Media Flow In State Changed Event] ::: Media Flow In State Changed [{}]", event.getState());
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

      webRtcEndpoint.addMediaFlowInStateChangeListener(new EventListener<MediaFlowInStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowInStateChangeEvent event) {
          log.info("[Composite webRtcEP Media Flow In State Changed Event] ::: Media Flow In State Changed [{}]", event.getState());
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

      webRtcEndpoint.addMediaFlowOutStateChangeListener(new EventListener<MediaFlowOutStateChangeEvent>() {

        @Override
        public void onEvent(MediaFlowOutStateChangeEvent event) {
          log.info("[Composite webRtcEP Media Flow Out State Changed Event] ::: Media Flow Out State Changed [{}]", event.getState());
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

      recorder.addUriEndpointStateChangedListener(new EventListener<UriEndpointStateChangedEvent>() {

        @Override
        public void onEvent(UriEndpointStateChangedEvent event) {
          log.info("[Composite Recording Uri Endpoint State Changed Event] ::: Uri Endpoint State Changed [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "uriEndpointStateChanged");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recorder.addMediaTranscodingStateChangeListener(new EventListener<MediaTranscodingStateChangeEvent>() {

        @Override
        public void onEvent(MediaTranscodingStateChangeEvent event) {
          log.info("[Composite Recording Media Transcoding State Changed Event] ::: Media Transcoding State Changed [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaTranscodingStateChanged");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      inHubPort.addMediaTranscodingStateChangeListener(new EventListener<MediaTranscodingStateChangeEvent>() {

        @Override
        public void onEvent(MediaTranscodingStateChangeEvent event) {
          log.info("[Composite inHubPort Media Transcoding State Changed Event] ::: Media Transcoding State Changed [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaTranscodingStateChanged");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      outHubPort.addMediaTranscodingStateChangeListener(new EventListener<MediaTranscodingStateChangeEvent>() {

        @Override
        public void onEvent(MediaTranscodingStateChangeEvent event) {
          log.info("[Composite outHubPort Media Transcoding State Changed Event] ::: Media Transcoding State Changed [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaTranscodingStateChanged");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });
      recHubPort.addMediaTranscodingStateChangeListener(new EventListener<MediaTranscodingStateChangeEvent>() {

        @Override
        public void onEvent(MediaTranscodingStateChangeEvent event) {
          log.info("[Composite recHubPort Media Transcoding State Changed Event] ::: Media Transcoding State Changed [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaTranscodingStateChanged");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      inHubPort.addElementConnectedListener(new EventListener<ElementConnectedEvent>() {

        @Override
        public void onEvent(ElementConnectedEvent event) {
          log.info("[Composite inHubPort Element Connected Event] ::: element Connected");
          JsonObject response = new JsonObject();
          response.addProperty("id", "elementConnected");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      inHubPort.addElementDisconnectedListener(new EventListener<ElementDisconnectedEvent>() {

        @Override
        public void onEvent(ElementDisconnectedEvent event) {
          log.info("[Composite inHubPort Element Disconnected Event] ::: element Disconnected");
          JsonObject response = new JsonObject();
          response.addProperty("id", "elementDisconnected");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      outHubPort.addElementConnectedListener(new EventListener<ElementConnectedEvent>() {

        @Override
        public void onEvent(ElementConnectedEvent event) {
          log.info("[Composite outHubPort Element Connected Event] ::: element Connected");
          JsonObject response = new JsonObject();
          response.addProperty("id", "elementConnected");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      outHubPort.addElementDisconnectedListener(new EventListener<ElementDisconnectedEvent>() {

        @Override
        public void onEvent(ElementDisconnectedEvent event) {
          log.info("[Composite outHubPort Element Disconnected Event] ::: element Disconnected");
          JsonObject response = new JsonObject();
          response.addProperty("id", "elementDisconnected");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });
      recHubPort.addElementConnectedListener(new EventListener<ElementConnectedEvent>() {

        @Override
        public void onEvent(ElementConnectedEvent event) {
          log.info("[Composite recHubPort Element Connected Event] ::: element Connected");
          JsonObject response = new JsonObject();
          response.addProperty("id", "elementConnected");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      recHubPort.addElementDisconnectedListener(new EventListener<ElementDisconnectedEvent>() {

        @Override
        public void onEvent(ElementDisconnectedEvent event) {
          log.info("[Composite recHubPort Element Disconnected Event] ::: element Disconnected");
          JsonObject response = new JsonObject();
          response.addProperty("id", "elementDisconnected");
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
        }

      });

      //webRtcEp to inPort
      connectRtpToPortAccordingToProfile(inHubPort, webRtcEndpoint, profile);
      //outPort to webRtcEp
      connectPortToEpAccordingToProfile(outHubPort, webRtcEndpoint, profile);
      //recPort to recorderEp
      connectPortToEpAccordingToProfile(recHubPort, recorder, profile);
      // connectRtpToPortAccordingToProfile(recHubPort, recorder, profile);

      //mp3 only
      webRtcEndpoint.connect(recorderAudio, MediaType.AUDIO);

      // Store user session
      UserSession user = new UserSession(session);
      user.setId(jsonMessage.get("id").getAsString());
      user.setMediaPipeline(pipeline);
      user.setWebRtcEndpoint(webRtcEndpoint);
      user.setRecorderEndpoint(recorder);
      user.setComposite(composite);
      registry.register(user);

      this.userLocal = user;

      // SDP negotiation
      String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
      String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);

      // Gather ICE candidates
      webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

        @Override
        public void onEvent(IceCandidateFoundEvent event) {
          log.info("[Composite Ice Candidate Found Event] ::: Ice Candidate Founded");
          JsonObject response = new JsonObject();
          response.addProperty("id", "iceCandidate");
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
      response.addProperty("id", "startResponse");
      response.addProperty("sdpAnswer", sdpAnswer);

      synchronized (user) {
        session.sendMessage(new TextMessage(response.toString()));
      }

      webRtcEndpoint.gatherCandidates();

      recorder.record();
      recorderAudio.record();

    }catch(Throwable t){
      log.error("Composite Start error", t);
      sendError(session, t.getMessage());
    }
  }

  private void connectRtpToPortAccordingToProfile(HubPort hubPort, Endpoint endPoint, MediaProfileSpecType profile){
    switch (profile) {
      case WEBM:
       endPoint.connect(hubPort, MediaType.AUDIO);
       endPoint.connect(hubPort, MediaType.VIDEO);
        break;
      case WEBM_AUDIO_ONLY:
        endPoint.connect(hubPort, MediaType.AUDIO);
        break;
      case WEBM_VIDEO_ONLY:
        endPoint.connect(hubPort, MediaType.VIDEO);
        break;
      default:
        throw new UnsupportedOperationException("Unsupported profile for this tutorial: " + profile);
    }
  }

  private void connectPortToEpAccordingToProfile(HubPort hubPort, Endpoint endPoint, MediaProfileSpecType profile){
    switch (profile) {
      case WEBM:
        hubPort.connect(endPoint, MediaType.AUDIO);
        hubPort.connect(endPoint, MediaType.VIDEO);
        break;
      case WEBM_AUDIO_ONLY:
        hubPort.connect(endPoint, MediaType.AUDIO);
        break;
      case WEBM_VIDEO_ONLY:
        hubPort.connect(endPoint, MediaType.VIDEO);
        break;
      default:
        throw new UnsupportedOperationException("Unsupported profile for this tutorial: " + profile);
    }
  }

  private void disconnect(WebSocketSession session){
    userLocal.getMediaPipeline().release();
    // try {
    //   registry.getById("start").getMediaPipeline().release();
    //   kurento.destroy();
    //   session.close();
    // } catch (IOException e) {
    //   e.printStackTrace();
    // }
  }
}//class
