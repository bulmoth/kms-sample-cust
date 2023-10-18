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

package org.kurento.tutorial.one2onecallrec;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.Composite;
import org.kurento.client.EndOfStreamEvent;
import org.kurento.client.EventListener;
import org.kurento.client.HttpPostEndpoint;
import org.kurento.client.HubPort;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaProfileSpecType;
import org.kurento.client.MediaType;
import org.kurento.client.PlayerEndpoint;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.WebRtcEndpoint;
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

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;

/**
 * Protocol handler for 1 to 1 video call communication.
 *
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @author Micael Gallego (micael.gallego@gmail.com)
 * @since 6.1.1
 */
public class CallHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(CallHandler.class);
  private static final Gson gson = new GsonBuilder().create();

  private final ConcurrentHashMap<String, MediaPipeline> pipelines = new ConcurrentHashMap<>();

  private static Boolean isComposite;
  private int count = 0;

  @Autowired
  private KurentoClient kurento;

  @Autowired
  private UserRegistry registry;

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
    UserSession user = registry.getBySession(session);
    
    isComposite = false;

    String recordMode = jsonMessage.get("recordMode").getAsString();
    if("composite".equals(recordMode)){
      isComposite = true;
    }

    if (user != null) {
      log.debug("Incoming message from user '{}': {}", user.getName(), jsonMessage);
    } else {
      log.debug("Incoming message from new user: {}", jsonMessage);
    }

    switch (jsonMessage.get("id").getAsString()) {
      case "register":
        register(session, jsonMessage);
        break;
      case "call":
        call(user, jsonMessage);
        break;
      case "call2":
        call2(user, jsonMessage);
      break;
      case "incomingCallResponse":
        incomingCallResponse(user, jsonMessage);
        break;
      case "incomingCallResponse2":
        incomingCallResponse2(user, jsonMessage);
      break;
      case "play":
        play(user, jsonMessage);
        break;
      case "onIceCandidate": {
        JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();

        if (user != null) {
          IceCandidate cand =
              new IceCandidate(candidate.get("candidate").getAsString(), candidate.get("sdpMid")
                  .getAsString(), candidate.get("sdpMLineIndex").getAsInt());
          user.addCandidate(cand);
        }
        break;
      }
      case "stop":
        stop(session);
        // releaseAll(user);
        releasePipeline(user);
        break;
      case "stopPlay":
        releasePipeline(user);
        break;
      case "pause":
        pause(user);
        break;
      case "resume":
        resume(user);
        break;
      case "clean":
       registry.clean();
      break;
      case "release":
       pipelines.get(user.getSessionId()).release();
       if(user.getPipeline().getPipeline() != null){
        user.getPipeline().getPipeline().release();
       }
      break;
      case "notFlowingVideo":
       user.getPipeline().notFlowingVideo(user, isComposite);
      break;
      case "notFlowingAudio":
      user.getPipeline().notFlowingAudio(user, isComposite);
      break;
      case "flowingVideo":
      user.getPipeline().flowingVideo(user, isComposite);
      break;
      case "flowingAudio":
      user.getPipeline().flowingAudio(user, isComposite);
      break;
      case "reOffer":
       reOffer(user, jsonMessage);
      break;
      case "concat":
      try{
        concatFile(user);
        }catch(IOException e){
          e.printStackTrace();
        }
        break;
      case "newRecEp":
        newRecEp(user);
        break;
      case "playHttp":
        play2(user, jsonMessage);
        break;
      case "outHubPlay":
      outHubPlay(user, jsonMessage);
      break;
      default:
        break;
    }
  }

  private void register(WebSocketSession session, JsonObject jsonMessage) throws IOException {
    String name = jsonMessage.getAsJsonPrimitive("name").getAsString();

    UserSession caller = new UserSession(session, name);
    String responseMsg = "accepted";
    if (name.isEmpty()) {
      responseMsg = "rejected: empty user name";
    } else if (registry.exists(name)) {
      responseMsg = "rejected: user '" + name + "' already registered";
    } else {
      registry.register(caller);
    }

    JsonObject response = new JsonObject();
    response.addProperty("id", "registerResponse");
    response.addProperty("response", responseMsg);
    caller.sendMessage(response);
  }

  private void call(UserSession caller, JsonObject jsonMessage) throws IOException {
    String to = jsonMessage.get("to").getAsString();
    String from = jsonMessage.get("from").getAsString();
    JsonObject response = new JsonObject();

    if (registry.exists(to)) {
      caller.setSdpOffer(jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString());
      caller.setCallingTo(to);

      response.addProperty("id", "incomingCall");
      response.addProperty("from", from);
      response.addProperty("to", to); //shj

      UserSession callee = registry.getByName(to);
      callee.sendMessage(response);
      callee.setCallingFrom(from);
    } else {
      response.addProperty("id", "callResponse");
      response.addProperty("response", "rejected");
      response.addProperty("message", "user '" + to + "' is not registered");

      caller.sendMessage(response);
    }
  }

  private void call2(UserSession callee, JsonObject jsonMessage) throws IOException {
    String to = jsonMessage.get("to").getAsString();
    String from = jsonMessage.get("from").getAsString();
    JsonObject response = new JsonObject();

    if (registry.exists(from)) {
      callee.setSdpOffer(jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString());
      callee.setCallingTo(to);

      response.addProperty("id", "incomingCall2");
      response.addProperty("from", from);
      response.addProperty("to", to); //shj

      UserSession caller = registry.getByName(from);
      caller.sendMessage(response);
      caller.setCallingFrom(from);
    } else {
      response.addProperty("id", "callResponse");
      response.addProperty("response", "rejected");
      response.addProperty("message", "user '" + from + "' is not registered");

      callee.sendMessage(response);
    }
  }

  private void incomingCallResponse(final UserSession callee, JsonObject jsonMessage)
      throws IOException {
    String callResponse = jsonMessage.get("callResponse").getAsString();
    String from = jsonMessage.get("from").getAsString();
    String to = jsonMessage.get("to").getAsString(); //shj
    // final UserSession calleer = registry.getByName(to); //shj
    final UserSession calleer = registry.getByName(from); //caller임
    // String to = calleer.getCallingFrom();

    if ("accept".equals(callResponse)) {
      log.info("Accepted call from '{}' to '{}'", from, to);

      CallMediaPipeline callMediaPipeline = new CallMediaPipeline(kurento, from, to, isComposite);
      callee.setPipeline(callMediaPipeline);
      calleer.setPipeline(callMediaPipeline);

      pipelines.put(calleer.getSessionId(), callMediaPipeline.getPipeline());
      pipelines.put(callee.getSessionId(), callMediaPipeline.getPipeline());

      callee.setWebRtcEndpointRecv(callMediaPipeline.getCalleeWebRtcEpRecv());
      callee.setWebRtcEndpoint(callMediaPipeline.getCalleeWebRtcEp());
      
      if(isComposite){
        callee.setRecorderEndpoint(callMediaPipeline.getCompositeRecEP());
      }else{
        callee.setRecorderEndpoint(callMediaPipeline.getRecorderCallee()); //shj
      }

      callMediaPipeline.getCalleeWebRtcEpRecv().addIceCandidateFoundListener(
          new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
              JsonObject response = new JsonObject();
              response.addProperty("id", "iceCandidateRecv");
              response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
              try {
                synchronized (callee.getSession()) {
                  callee.getSession().sendMessage(new TextMessage(response.toString()));
                }
              } catch (IOException e) {
                log.info(e.getMessage());
              }
            }
          });

      log.info("Callee Client ice Candidate Complete");
      String calleeSdpOfferRecv = jsonMessage.get("sdpOffer").getAsString();
      String calleeSdpAnswerRecv = callMediaPipeline.generateSdpAnswerForCalleeRecv(calleeSdpOfferRecv);
      JsonObject startCommunicationRecv = new JsonObject();
      startCommunicationRecv.addProperty("id", "startCommunicationRecv");
      startCommunicationRecv.addProperty("sdpAnswer", calleeSdpAnswerRecv);

      synchronized (callee) {
        callee.sendMessage(startCommunicationRecv);
      }

      log.info("Callee startCommunication Complete");
      callMediaPipeline.getCalleeWebRtcEpRecv().gatherCandidates();

      // String callerSdpOffer = registry.getByName(from).getSdpOffer(); //shj
      String callerSdpOffer = calleer.getSdpOffer();

      calleer.setWebRtcEndpoint(callMediaPipeline.getCallerWebRtcEp());
      calleer.setWebRtcEndpointRecv(callMediaPipeline.getCallerWebRtcEpRecv());

      if(isComposite){
        calleer.setRecorderEndpoint(callMediaPipeline.getCompositeRecEP());
      }else{
        calleer.setRecorderEndpoint(callMediaPipeline.getRecorderCaller());
      }

      callMediaPipeline.getCallerWebRtcEp().addIceCandidateFoundListener(
          new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
              JsonObject response = new JsonObject();
              response.addProperty("id", "iceCandidate");
              response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
              try {
                synchronized (calleer.getSession()) {
                  calleer.getSession().sendMessage(new TextMessage(response.toString()));
                }
              } catch (IOException e) {
                log.info(e.getMessage());
              }
            }
          });

      String callerSdpAnswer = callMediaPipeline.generateSdpAnswerForCaller(callerSdpOffer);

      JsonObject response = new JsonObject();
      response.addProperty("id", "callResponse");
      response.addProperty("response", "accepted");
      response.addProperty("sdpAnswer", callerSdpAnswer);

      synchronized (calleer) {
        calleer.sendMessage(response);
      }

      callMediaPipeline.getCallerWebRtcEp().gatherCandidates();

      callMediaPipeline.record(isComposite);

    } else {
      JsonObject response = new JsonObject();
      response.addProperty("id", "callResponse");
      response.addProperty("response", "rejected");
      calleer.sendMessage(response);
    }
  }

  private void incomingCallResponse2(final UserSession caller, JsonObject jsonMessage)
      throws IOException {
    String callResponse = jsonMessage.get("callResponse").getAsString();
    String from = jsonMessage.get("from").getAsString();
    String to = jsonMessage.get("to").getAsString(); //shj
    // final UserSession calleer = registry.getByName(to); //shj
    final UserSession calleer = registry.getByName(to); //callee임
    // String to = calleer.getCallingFrom();

    if ("accept".equals(callResponse)) {
      log.info("Accepted call from '{}' to '{}'", from, to);

      //CallMediaPipeline callMediaPipeline = new CallMediaPipeline(kurento, from, to, isComposite);
      // pipelines.put(calleer.getSessionId(), callMediaPipeline.getPipeline());
      // pipelines.put(caller.getSessionId(), callMediaPipeline.getPipeline());
      CallMediaPipeline callMediaPipeline = caller.getPipeline();

      //caller.setWebRtcEndpointRecv(callMediaPipeline.getCallerWebRtcEpRecv());
      
      if(isComposite){
        caller.setRecorderEndpoint(callMediaPipeline.getCompositeRecEP());
      }else{
        caller.setRecorderEndpoint(callMediaPipeline.getRecorderCaller()); //shj
      }

      callMediaPipeline.getCallerWebRtcEpRecv().addIceCandidateFoundListener(
          new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
              JsonObject response = new JsonObject();
              response.addProperty("id", "iceCandidateRecv");
              response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
              try {
                synchronized (caller.getSession()) {
                  caller.getSession().sendMessage(new TextMessage(response.toString()));
                }
              } catch (IOException e) {
                log.info(e.getMessage());
              }
            }
          });

      log.info("Caller Client ice Candidate Complete");
      String callerSdpOfferRecv = jsonMessage.get("sdpOffer").getAsString();
      String callerSdpAnswerRecv = callMediaPipeline.generateSdpAnswerForCallerRecv(callerSdpOfferRecv);
      JsonObject startCommunicationRecv = new JsonObject();
      startCommunicationRecv.addProperty("id", "startCommunicationRecv");
      startCommunicationRecv.addProperty("sdpAnswer", callerSdpAnswerRecv);

      synchronized (caller) {
        caller.sendMessage(startCommunicationRecv);
      }

      log.info("Caller startCommunication Complete");
      callMediaPipeline.getCallerWebRtcEpRecv().gatherCandidates();

      // String callerSdpOffer = registry.getByName(from).getSdpOffer(); //shj
      String calleeSdpOffer = calleer.getSdpOffer();

      //calleer.setWebRtcEndpoint(callMediaPipeline.getCalleeWebRtcEp());

      if(isComposite){
        calleer.setRecorderEndpoint(callMediaPipeline.getCompositeRecEP());
      }else{
        calleer.setRecorderEndpoint(callMediaPipeline.getRecorderCallee());
      }

      callMediaPipeline.getCalleeWebRtcEp().addIceCandidateFoundListener(
          new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
              JsonObject response = new JsonObject();
              response.addProperty("id", "iceCandidate");
              response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
              try {
                synchronized (calleer.getSession()) {
                  calleer.getSession().sendMessage(new TextMessage(response.toString()));
                }
              } catch (IOException e) {
                log.info(e.getMessage());
              }
            }
          });
      
      log.info("callee icecandidate complete2222");

      String calleeSdpAnswer = callMediaPipeline.generateSdpAnswerForCallee(calleeSdpOffer);

      JsonObject response = new JsonObject();
      response.addProperty("id", "callResponse");
      response.addProperty("response", "accepted");
      response.addProperty("sdpAnswer", calleeSdpAnswer);

      synchronized (calleer) {
        calleer.sendMessage(response);
      }

      callMediaPipeline.getCalleeWebRtcEp().gatherCandidates();
      log.info("callee gathering candidates complete");

      callMediaPipeline.record(isComposite);

    } else {
      JsonObject response = new JsonObject();
      response.addProperty("id", "callResponse");
      response.addProperty("response", "rejected");
      calleer.sendMessage(response);
    }
  }


  public void stop(WebSocketSession session) throws IOException {
    // Both users can stop the communication. A 'stopCommunication'
    // message will be sent to the other peer.
    UserSession stopperUser = registry.getBySession(session);
    UserSession stoppedUser =
    (stopperUser.getCallingFrom() != null) ? registry.getByName(stopperUser.getCallingFrom())
        : stopperUser.getCallingTo() != null ? registry.getByName(stopperUser.getCallingTo())
            : null;

    if(stopperUser.getRecorderEndpoint() != null){
      stopperUser.getRecorderEndpoint().stop();
      stopperUser.getRecorderEndpoint().release();
    }

    if(stoppedUser.getRecorderEndpoint() != null){
      stoppedUser.getRecorderEndpoint().stop();
      stoppedUser.getRecorderEndpoint().release();
    }

    if (pipelines.containsKey(session.getId())) {
      pipelines.get(session.getId()).release();
      log.info("{} 's pipeline is released [function stop()]", session.getId());
      pipelines.remove(session.getId());
    }


    if (stopperUser != null) {
      stoppedUser =
          (stopperUser.getCallingFrom() != null) ? registry.getByName(stopperUser.getCallingFrom())
              : stopperUser.getCallingTo() != null ? registry.getByName(stopperUser.getCallingTo())
                  : null;

              if (stoppedUser != null) {
                JsonObject message = new JsonObject();
                message.addProperty("id", "stopCommunication");
                stoppedUser.sendMessage(message);
                stoppedUser.clear();
              }
              stopperUser.clear();
    }

    JsonObject response = new JsonObject();
    response.addProperty("id", "makeUrl");
    response.addProperty("videoUrl", stopperUser.getPipeline().RECORDING_PATH+ stopperUser.getCallingFrom() + "-" + stopperUser.getCallingTo() + "-concat" + stopperUser.getPipeline().RECORDING_EXT);
    session.sendMessage(new TextMessage(response.toString()));
  }

  public void releasePipeline(UserSession session) {
    String sessionId = session.getSessionId();

    if (pipelines.containsKey(sessionId)) {
      pipelines.get(sessionId).release();
      if(session.getPipeline().getPipeline() != null){
        session.getPipeline().getPipeline().release();
      }
      log.info("{} 's pipeline is released [function releasePipeline()]", sessionId);
      pipelines.remove(sessionId);
    }
    session.setWebRtcEndpoint(null);
    session.setWebRtcEndpointRecv(null);
    session.setPlayingWebRtcEndpoint(null);

    // set to null the endpoint of the other user
    UserSession stoppedUser =
        (session.getCallingFrom() != null) ? registry.getByName(session.getCallingFrom())
            : registry.getByName(session.getCallingTo());

        

        if(pipelines.get(stoppedUser.getSessionId()) != null){
          pipelines.get(stoppedUser.getSessionId()).release(); //shj
          if(stoppedUser.getPipeline().getPipeline() != null){
            stoppedUser.getPipeline().getPipeline().release();
          }
        }
        log.info("{} 's pipeline is released 2", sessionId);
        
        stoppedUser.setWebRtcEndpoint(null);
        stoppedUser.setWebRtcEndpointRecv(null);
        stoppedUser.setPlayingWebRtcEndpoint(null);
  }

  private void play(final UserSession session, JsonObject jsonMessage) throws IOException {
    String user = jsonMessage.get("user").getAsString();
    log.debug("Playing recorded call of user '{}'", user);
    String url = jsonMessage.get("url").getAsString();

    JsonObject response = new JsonObject();
    response.addProperty("id", "playResponse");

    if (registry.getByName(user) != null && registry.getBySession(session.getSession()) != null) {
      final PlayMediaPipeline playMediaPipeline =
          new PlayMediaPipeline(kurento, session, session.getSession(), isComposite, url, true);

      session.setPlayingWebRtcEndpoint(playMediaPipeline.getWebRtc());

      playMediaPipeline.getPlayer().addEndOfStreamListener(new EventListener<EndOfStreamEvent>() {
        @Override
        public void onEvent(EndOfStreamEvent event) {
          UserSession user = registry.getBySession(session.getSession());
          releasePipeline(user);
          playMediaPipeline.sendPlayEnd(session.getSession());
        }
      });

      playMediaPipeline.getWebRtc().addIceCandidateFoundListener(
          new EventListener<IceCandidateFoundEvent>() {

            @Override
            public void onEvent(IceCandidateFoundEvent event) {
              JsonObject response = new JsonObject();
              response.addProperty("id", "iceCandidate");
              response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
              try {
                synchronized (session) {
                  session.getSession().sendMessage(new TextMessage(response.toString()));
            }
              } catch (IOException e) {
                log.debug(e.getMessage());
              }
            }
          });

      String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
      String sdpAnswer = playMediaPipeline.generateSdpAnswer(sdpOffer);

      response.addProperty("response", "accepted");

      response.addProperty("sdpAnswer", sdpAnswer);

      response.addProperty("httpUrl", playMediaPipeline.getHttpUrl());

      playMediaPipeline.play();
      pipelines.put(session.getSessionId(), playMediaPipeline.getPipeline());
      synchronized (session.getSession()) {
        session.sendMessage(response);
      }

      playMediaPipeline.getWebRtc().gatherCandidates();

    } else {
      response.addProperty("response", "rejected");
      response.addProperty("error", "No recording for user '" + user
          + "'. Please type a correct user in the 'Peer' field.");
      session.getSession().sendMessage(new TextMessage(response.toString()));
    }
  }

  private void play2(final UserSession session, JsonObject jsonMessage) throws IOException {
    String user = jsonMessage.get("user").getAsString();
    log.debug("Playing recorded call of user '{}'", user);
    String url = jsonMessage.get("url").getAsString();

    if (registry.getByName(user) != null && registry.getBySession(session.getSession()) != null) {
      final PlayMediaPipeline playMediaPipeline =
          new PlayMediaPipeline(kurento, session, session.getSession(), isComposite, url, false);

      HttpPostEndpoint hpEp = playMediaPipeline.getHpEp();
      PlayerEndpoint playEp = playMediaPipeline.getPlayer();

      playMediaPipeline.getPlayer().addEndOfStreamListener(new EventListener<EndOfStreamEvent>() {
        @Override
        public void onEvent(EndOfStreamEvent event) {
          UserSession user = registry.getBySession(session.getSession());
          releasePipeline(user);
          playMediaPipeline.sendPlayEnd(session.getSession());
        }
      });

      playEp.connect(hpEp);
      playEp.play();

    } else {
      log.debug("no User");
    }
  }

private void outHubPlay(final UserSession session, JsonObject jsonMessage) throws IOException {
    String from = jsonMessage.get("from").getAsString();
    String to = jsonMessage.get("to").getAsString();
    String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
    log.debug("Playing OutHub");

    MediaPipeline pipeline = session.getPipeline().getPipeline();
    Composite composite = session.getPipeline().getComposite();

    WebRtcEndpoint ep = new WebRtcEndpoint.Builder(pipeline).build();
    HubPort outHub = new HubPort.Builder(composite).build();

    String sdpAnswer = ep.processOffer(sdpOffer);

    outHub.connect(ep);

    ep.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {
      @Override
      public void onEvent(IceCandidateFoundEvent event) {
        JsonObject response = new JsonObject();
        response.addProperty("id", "iceCandidateOutHub");
        response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
        try {
          synchronized (session) {
            session.getSession().sendMessage(new TextMessage(response.toString()));
          }
        } catch (IOException e) {
          log.info(e.getMessage());
        }
      }
    });

    JsonObject response = new JsonObject();
    response.addProperty("id", "startOutHubPlay");
    response.addProperty("sdpAnswer", sdpAnswer);
    session.sendMessage(response);

    ep.gatherCandidates();

    outHub.connect(ep);
  }


  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    stop(session);
    registry.removeBySession(session);
  }

  private void pause(UserSession user){
    user.getRecorderEndpoint().pause();
    user.getPipeline().getRecorderAudioFrom().pause();
    user.getPipeline().getRecorderAudioTo().pause();
  }

  private void resume(UserSession user){
    user.getRecorderEndpoint().record();
    user.getPipeline().getRecorderAudioFrom().record();
    user.getPipeline().getRecorderAudioTo().record();
  }

  private void reOffer(UserSession user, JsonObject jsonMessage) throws IOException {
    String to = jsonMessage.get("to").getAsString();
    // String from = jsonMessage.get("from").getAsString();
    // String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();
    // JsonObject response = new JsonObject();

    // if (registry.exists(to) && registry.exists(from)) {
    //   String sdpAnswer = user.getWebRtcEndpoint().processOffer(sdpOffer);
    //   response.addProperty("id", "reAnswer");
    //   response.addProperty("sdpAnswer", sdpAnswer);
    //   synchronized(user){
    //     user.sendMessage(response);
    //   }
    // }
    // JsonObject track = jsonMessage.get("track").getAsJsonObject();
    // String trackStr = track.toString();
    // String trackStr = jsonMessage.get("track").getAsString();
    // log.info(trackStr);
    JsonObject response = new JsonObject();
    response.addProperty("id", "reAnswer");
    // response.addProperty("track", trackStr);
    registry.getByName(to).sendMessage(response);
  }

  private synchronized void concatFile(UserSession user) throws IOException{
    FFmpeg ffmpeg = new FFmpeg("/usr/bin/ffmpeg");
    FFprobe ffprobe = new FFprobe("/usr/bin/ffprobe");
    FFmpegBuilder builder = null;
    FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);

    // if(user.getPipeline().flag > 0){ //오디오
      
    //   String inputFile = "";
    //   for(int i=0; i<user.getPipeline().flag; i++){
    //     inputFile = "/fermi/storage" + user.getPipeline().AUDIO_PATH.substring(11) + user.getName() + "-" + i + user.getPipeline().AUDIO_EXT;
    //     builder = new FFmpegBuilder()
    //               .overrideOutputFiles(true)
    //               .addInput(inputFile)
    //               .addOutput("/fermi/storage/concat" + i + user.getPipeline().AUDIO_EXT)
    //               .setVideoMovFlags("+faststart")
    //               .done();
    //     executor.createJob(builder).run();
    //   }
    //   String fullName = "concat:";
    //   for(int i=0; i<user.getPipeline().flag; i++){
    //     fullName += "/fermi/storage/concat" + i + user.getPipeline().AUDIO_EXT + "|";
    //   }
    //   fullName += "/fermi/storage/concat" + user.getPipeline().flag + user.getPipeline().AUDIO_EXT;
    //   FFmpegBuilder concatBuilder = new FFmpegBuilder()
    //                                 .overrideOutputFiles(true)
    //                                 .addInput(fullName)
    //                                 .addOutput("/fermi/storage" + user.getPipeline().AUDIO_PATH.substring(11) + user.getName() + "-concat" + user.getPipeline().AUDIO_EXT)
    //                                 .setVideoMovFlags("+faststart")
    //                                 .done();
    //   executor.createJob(concatBuilder).run();
    // }

    if(user.getPipeline().flag2 > 0){ //비디오
      
      String inputFile = "";
      for(int i=0; i<user.getPipeline().flag2; i++){
        inputFile = "/fermi/storage" + user.getPipeline().RECORDING_PATH.substring(11) + user.getCallingFrom() + "-" + user.getCallingTo() + "-" + i + user.getPipeline().RECORDING_EXT;
        builder = new FFmpegBuilder()
                  .overrideOutputFiles(true)
                  .addInput(inputFile)
                  .addOutput("/fermi/storage/concat" + i + user.getPipeline().RECORDING_EXT)
                  .setVideoMovFlags("+faststart")
                  .done();
        executor.createJob(builder).run();
      }
      FFmpegBuilder concatBuilder = new FFmpegBuilder().overrideOutputFiles(true);
      String fullName = "";
      String filterComplex = "";
      for(int i=0; i<user.getPipeline().flag2; i++){
        fullName = "/fermi/storage/concat" + i + user.getPipeline().RECORDING_EXT;
        concatBuilder.addInput(fullName);
        filterComplex += "[" + i + ":v]" + "[" + i + ":a]";
      }
      filterComplex += "concat=n=" + (user.getPipeline().flag2) + ":v=1:a=1[v][a]";
      concatBuilder.setComplexFilter(filterComplex)
                    .addOutput("/fermi/storage" + user.getPipeline().RECORDING_PATH.substring(11) + user.getCallingFrom() + "-" + user.getCallingTo() + "-concat" + user.getPipeline().RECORDING_EXT)
                    .setVideoMovFlags("+faststart")
                    .addExtraArgs("-map", "[v]")
                    .addExtraArgs("-map", "[a]")
                    .done();
      executor.createJob(concatBuilder).run();
    }
  }

  private void newRecEp(UserSession user){
    if(user.getPipeline() != null){
      MediaPipeline pipeline = user.getPipeline().getPipeline();
      RecorderEndpoint newRecEp = new RecorderEndpoint.Builder(pipeline, user.getPipeline().RECORDING_PATH + "newRecEp" + count + user.getPipeline().RECORDING_EXT).build();
      RecorderEndpoint newAudioRecEp = new RecorderEndpoint.Builder(pipeline, user.getPipeline().AUDIO_PATH + "newAudioRecEp" + count + user.getPipeline().AUDIO_EXT).withMediaProfile(MediaProfileSpecType.MP4_AUDIO_ONLY).build();
      count++;

      user.getWebRtcEndpoint().connect(newRecEp, MediaType.AUDIO);
      user.getWebRtcEndpoint().connect(newRecEp, MediaType.VIDEO);

      user.getWebRtcEndpoint().connect(newAudioRecEp, MediaType.AUDIO);

      newRecEp.record();
      newAudioRecEp.record();
    }
  }

  private void releaseAll(UserSession user){
    if(user.getPipeline() != null){
      if(user.getRecorderEndpoint() != null) user.getRecorderEndpoint().release();
      if(user.getWebRtcEndpoint() != null) user.getWebRtcEndpoint().release();
      if(user.getWebRtcEndpointRecv() != null) user.getWebRtcEndpointRecv().release();
      if(user.getPipeline().getPipeline() != null) user.getPipeline().getPipeline().release();
    }
  }

}//class
