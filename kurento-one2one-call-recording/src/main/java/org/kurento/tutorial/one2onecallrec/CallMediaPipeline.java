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
 */

package org.kurento.tutorial.one2onecallrec;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.kurento.client.Composite;
import org.kurento.client.ErrorEvent;
import org.kurento.client.EventListener;
import org.kurento.client.HubPort;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaFlowInStateChangedEvent;
import org.kurento.client.MediaFlowOutStateChangedEvent;
import org.kurento.client.MediaFlowState;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaProfileSpecType;
import org.kurento.client.MediaType;
import org.kurento.client.PausedEvent;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.RecordingEvent;
import org.kurento.client.StoppedEvent;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

/**
 * Media Pipeline (connection of Media Elements) for the advanced one to one video communication.
 * 
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @author Micael Gallego (micael.gallego@gmail.com)
 * @since 6.1.1
 */
public class CallMediaPipeline {

  private static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-S");
  public static final String RECORDING_PATH = "file:///tmp/" + df.format(new Date()) + "-";
  public static final String RECORDING_EXT = ".webm";
  // public static final String RECORDING_EXT = ".mp4";

  public static final String AUDIO_PATH = "file:///tmp/" + df.format(new Date()) + "-";
  // public static final String AUDIO_EXT = ".webm";
  public static final String AUDIO_EXT = ".mp4";

  private final MediaPipeline pipeline;
  private final WebRtcEndpoint webRtcCaller;
  private final WebRtcEndpoint webRtcCallee;
  private WebRtcEndpoint webRtcCallerRecv;
  private WebRtcEndpoint webRtcCalleeRecv;
  private final RecorderEndpoint recorderCaller;
  private final RecorderEndpoint recorderCallee;
  private static Composite composite;
  private static RecorderEndpoint compositeRecEP;
  private static RecorderEndpoint recorderAudioFrom;
  private static RecorderEndpoint recorderAudioTo;
  private HubPort inHubPortFrom;
  private HubPort inHubPortTo;
  private HubPort recHubPort;
  private int count = 0;
  public int flag = 0;
  public int flag2 = 0;

  private int min = 0;
  private int max = 0;

  private final Logger log = LoggerFactory.getLogger(CallMediaPipeline.class);

  public CallMediaPipeline(KurentoClient kurento, String from, String to, Boolean isComposite) {

    // Media pipeline
    pipeline = kurento.createMediaPipeline();

    // Media Elements (WebRtcEndpoint, RecorderEndpoint)
    webRtcCaller = new WebRtcEndpoint.Builder(pipeline).build();
    webRtcCallee = new WebRtcEndpoint.Builder(pipeline).build();

    setWebRtcProfile(webRtcCaller);
    setWebRtcProfile(webRtcCallee);

    webRtcCallerRecv = new WebRtcEndpoint.Builder(pipeline).build();
    webRtcCalleeRecv = new WebRtcEndpoint.Builder(pipeline).build();

    setWebRtcProfile(webRtcCallerRecv);
    setWebRtcProfile(webRtcCalleeRecv);

    pipeline.addErrorListener(new EventListener<ErrorEvent>() {
      @Override
      public void onEvent(ErrorEvent ev) {
        log.error("[MediaPipeline::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
            ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
            ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
      }
    });
    webRtcCaller.addErrorListener(new EventListener<ErrorEvent>() {
      @Override
      public void onEvent(ErrorEvent ev) {
        log.error("[webRtcCaller::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
            ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
            ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
      }
    });
    webRtcCallee.addErrorListener(new EventListener<ErrorEvent>() {
      @Override
      public void onEvent(ErrorEvent ev) {
        log.error("[webRtcCallee::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
            ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
            ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
      }
    });

    webRtcCallerRecv.addErrorListener(new EventListener<ErrorEvent>() {
      @Override
      public void onEvent(ErrorEvent ev) {
        log.error("[webRtcCallerRecv::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
            ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
            ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
      }
    });
    webRtcCalleeRecv.addErrorListener(new EventListener<ErrorEvent>() {
      @Override
      public void onEvent(ErrorEvent ev) {
        log.error("[webRtcCalleeRecv::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
            ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
            ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
      }
    });

    recorderCaller = new RecorderEndpoint.Builder(pipeline, RECORDING_PATH + from + RECORDING_EXT).withMediaProfile(MediaProfileSpecType.WEBM)
        .build();
    recorderCallee = new RecorderEndpoint.Builder(pipeline, RECORDING_PATH + to + RECORDING_EXT).withMediaProfile(MediaProfileSpecType.WEBM)
        .build();

    // 단위 : bps
    // recorderCaller.setMinOuputBitrate(min);
    // recorderCaller.setMaxOuputBitrate(max);

    // recorderCallee.setMinOuputBitrate(min);
    // recorderCallee.setMaxOuputBitrate(max);

    recorderCaller.addErrorListener(new EventListener<ErrorEvent>() {
      @Override
      public void onEvent(ErrorEvent ev) {
        log.error("[recorderCaller::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
            ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
            ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
      }
    });
    recorderCallee.addErrorListener(new EventListener<ErrorEvent>() {
      @Override
      public void onEvent(ErrorEvent ev) {
        log.error("[recorderCallee::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
            ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
            ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
      }
    });

    webRtcCaller.addMediaFlowOutStateChangedListener(new EventListener<MediaFlowOutStateChangedEvent>() {
      @Override
        public void onEvent(MediaFlowOutStateChangedEvent event) {
          log.info("[webRtcCaller Media Flow Out State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
    });
    webRtcCallee.addMediaFlowOutStateChangedListener(new EventListener<MediaFlowOutStateChangedEvent>() {
      @Override
        public void onEvent(MediaFlowOutStateChangedEvent event) {
          log.info("[webRtcCallee Media Flow Out State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
    });
    webRtcCaller.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
        public void onEvent(MediaFlowInStateChangedEvent event) {
          log.info("[webRtcCaller Media Flow In State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
    });
    webRtcCallee.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
        public void onEvent(MediaFlowInStateChangedEvent event) {
          log.info("[webRtcCallee Media Flow In State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
    });

    webRtcCallerRecv.addMediaFlowOutStateChangedListener(new EventListener<MediaFlowOutStateChangedEvent>() {
      @Override
        public void onEvent(MediaFlowOutStateChangedEvent event) {
          log.info("[webRtcCallerRecv Media Flow Out State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
    });
    webRtcCalleeRecv.addMediaFlowOutStateChangedListener(new EventListener<MediaFlowOutStateChangedEvent>() {
      @Override
        public void onEvent(MediaFlowOutStateChangedEvent event) {
          log.info("[webRtcCalleeRecv Media Flow Out State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
    });
    webRtcCallerRecv.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
        public void onEvent(MediaFlowInStateChangedEvent event) {
          log.info("[webRtcCallerRecv Media Flow In State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
    });
    webRtcCalleeRecv.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
        public void onEvent(MediaFlowInStateChangedEvent event) {
          log.info("[webRtcCalleeRecv Media Flow In State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
    });


    recorderCaller.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
        public void onEvent(MediaFlowInStateChangedEvent event) {
          log.info("[recorderCaller Media Flow In State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
    });
    recorderCallee.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
        public void onEvent(MediaFlowInStateChangedEvent event) {
          log.info("[recorderCallee Media Flow In State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
    });
    recorderCaller.addRecordingListener(new EventListener<RecordingEvent>() {
      @Override
      public void onEvent(RecordingEvent event) {
        log.info("[recorderCaller Recording Event] ::: recording");
        JsonObject response = new JsonObject();
        response.addProperty("id", "recording");
      }
    });
    recorderCaller.addStoppedListener(new EventListener<StoppedEvent>() {
      @Override
      public void onEvent(StoppedEvent event) {
        log.info("[recorderCaller Stopped Event] ::: stopped");
        JsonObject response = new JsonObject();
        response.addProperty("id", "stopped");
      }
    });
    recorderCaller.addPausedListener(new EventListener<PausedEvent>() {
      @Override
      public void onEvent(PausedEvent event) {
        log.info("[recorderCaller Paused Event] ::: paused");
        JsonObject response = new JsonObject();
        response.addProperty("id", "paused");
      }
    });
    recorderCallee.addRecordingListener(new EventListener<RecordingEvent>() {
      @Override
      public void onEvent(RecordingEvent event) {
        log.info("[recorderCallee Recording Event] ::: recording");
        JsonObject response = new JsonObject();
        response.addProperty("id", "recording");
      }
    });
    recorderCallee.addStoppedListener(new EventListener<StoppedEvent>() {
      @Override
      public void onEvent(StoppedEvent event) {
        log.info("[recorderCallee Stopped Event] ::: stopped");
        JsonObject response = new JsonObject();
        response.addProperty("id", "stopped");
      }
    });
    recorderCallee.addPausedListener(new EventListener<PausedEvent>() {
      @Override
      public void onEvent(PausedEvent event) {
        log.info("[recorderCallee Paused Event] ::: paused");
        JsonObject response = new JsonObject();
        response.addProperty("id", "paused");
      }
    });

    if(isComposite){
      composite = new Composite.Builder(pipeline).build();

      inHubPortFrom = new HubPort.Builder(composite).build();
      inHubPortTo = new HubPort.Builder(composite).build();
      recHubPort = new HubPort.Builder(composite).build();

      compositeRecEP = new RecorderEndpoint.Builder(pipeline, RECORDING_PATH + from + "-" + to + "-" + flag2 + RECORDING_EXT).withMediaProfile(MediaProfileSpecType.WEBM).build();
      recorderAudioFrom = new RecorderEndpoint.Builder(pipeline, AUDIO_PATH + from + "-" + flag + AUDIO_EXT).withMediaProfile(MediaProfileSpecType.MP4_AUDIO_ONLY).build();
      recorderAudioTo = new RecorderEndpoint.Builder(pipeline, AUDIO_PATH + to + "-" + flag + AUDIO_EXT).withMediaProfile(MediaProfileSpecType.MP4_AUDIO_ONLY).build();

      recorderAudioFrom.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent ev) {
          log.error("[recorderAudioFrom::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
              ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
              ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
        }
      });
      recorderAudioTo.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent ev) {
          log.error("[recorderAudioTo::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
              ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
              ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
        }
      });
      compositeRecEP.addErrorListener(new EventListener<ErrorEvent>() {
        @Override
        public void onEvent(ErrorEvent ev) {
          log.error("[compositeRecEP::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
              ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
              ev.getTimestampMillis(), ev.getTags(), ev.getDescription());
        }
      });
      webRtcCaller.addMediaFlowOutStateChangedListener(new EventListener<MediaFlowOutStateChangedEvent>() {
        @Override
          public void onEvent(MediaFlowOutStateChangedEvent event) {
            // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
            //   disconnectRecEp(recorderAudioFrom, webRtcCalleeRecv, from);
            // }else if(count>0 && MediaFlowState.FLOWING.equals(event.getState())){
            //   recorderAudioFrom = connectRecEp(pipeline, webRtcCalleeRecv, from);
            // }
            if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
              disconnectRecEpAv(compositeRecEP, recHubPort);
            }else if(count>0 && MediaFlowState.FLOWING.equals(event.getState())){
              compositeRecEP = connectRecEpAv(pipeline, recHubPort, from, to);
            }
      //     if(count<10){
      //       if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
      //         inHubPortFrom.release();
      //         inHubPortTo.release();
      //         compositeRecEP.stop();
      //         compositeRecEP.release();
      //         recHubPort.release();
      //         composite.release();
      //         flag2++;
      //         count++;
      //       }else if(count>0 && MediaFlowState.FLOWING.equals(event.getState())){
      //         composite = createNewComposite(pipeline, webRtcCaller, webRtcCallee, from, to);
      //       }
      //     }
        } 
      });
      webRtcCallee.addMediaFlowOutStateChangedListener(new EventListener<MediaFlowOutStateChangedEvent>() {
        @Override
          public void onEvent(MediaFlowOutStateChangedEvent event) {
            // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
            //   disconnectRecEp(recorderAudioTo, webRtcCallerRecv, to);
            // }else if(count>0 && MediaFlowState.FLOWING.equals(event.getState())){
            //   recorderAudioTo = connectRecEp(pipeline, webRtcCallerRecv, to);
            // }
            if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
              disconnectRecEpAv(compositeRecEP, recHubPort);
            }else if(count>0 && MediaFlowState.FLOWING.equals(event.getState())){
              compositeRecEP = connectRecEpAv(pipeline, recHubPort, from, to);
            }
            // if(count<10){
            //   if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
            //     compositeRecEP.stop();
            //     compositeRecEP.release();
            //     inHubPortFrom.release();
            //     inHubPortTo.release();
            //     recHubPort.release();
            //     composite.release();
            //     flag2++;
            //     count++;
            //   }else if(count>0 && MediaFlowState.FLOWING.equals(event.getState())){
            //     composite = createNewComposite(pipeline, webRtcCaller, webRtcCallee, from, to);
            //   }
            // }
          }
      });
      inHubPortFrom.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
        @Override
        public void onEvent(MediaFlowInStateChangedEvent event) {
          log.info("[Composite inHubPortFrom(Caller) Media Flow In State Changed Event] ::: [{}]", event.getState());
          // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
          //   inHubPortFrom = createNewHub(inHubPortFrom, composite, webRtcCaller);
          // }
          // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
          //   createNewRecEpHub(compositeRecEP, pipeline, recHubPort, composite, from, to, inHubPortFrom);
          // }
          // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
          //   recorderAudioFrom.pause();
          // }else{
          //   recorderAudioFrom.record();
          // }
          // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
          //   createNewRecAudio(recorderAudioFrom, pipeline, webRtcCaller, from);
          // }
          // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
          //   disconnectRecEp(recorderAudioFrom, webRtcCaller, from);
          // }else if(count>0 && MediaFlowState.FLOWING.equals(event.getState())){
          //   recorderAudioFrom = connectRecEp(pipeline, webRtcCaller, from);
          // }
          // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
          //   disconnectRecEpAv(compositeRecEP, recHubPort);
          // }else if(count>0 && MediaFlowState.FLOWING.equals(event.getState())){
          //   compositeRecEP = connectRecEpAv(pipeline, recHubPort, from, to);
          // }
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
      });
      inHubPortTo.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
        @Override
        public void onEvent(MediaFlowInStateChangedEvent event) {
          log.info("[Composite inHubPortTo(Callee) Media Flow In State Changed Event] ::: [{}]", event.getState());
          // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
          //   inHubPortTo = createNewHub(inHubPortTo, composite, webRtcCallee);
          // }
          // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
          //   createNewRecEpHub(compositeRecEP, pipeline, recHubPort, composite, from, to, inHubPortTo);
          // }
          // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
          //   recorderAudioTo.pause();
          // }else{
          //   recorderAudioTo.record();
          // }
          // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
          //   createNewRecAudio(recorderAudioTo, pipeline, webRtcCallee, to);
          // }
          // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
          //   disconnectRecEp(recorderAudioTo, webRtcCallee, to);
          // }else if(count>0 && MediaFlowState.FLOWING.equals(event.getState())){
          //   recorderAudioTo = connectRecEp(pipeline, webRtcCallee, to);
          // }
          // if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
          //   disconnectRecEpAv(compositeRecEP, recHubPort);
          // }else if(count>0 && MediaFlowState.FLOWING.equals(event.getState())){
          //   compositeRecEP = connectRecEpAv(pipeline, recHubPort, from, to);
          // }
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
      });
      recorderAudioFrom.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
        @Override
        public void onEvent(MediaFlowInStateChangedEvent event) {
          log.info("[recorderAudioFrom(Caller) Media Flow In State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
      });
      recorderAudioTo.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
        @Override
        public void onEvent(MediaFlowInStateChangedEvent event) {
          log.info("[recorderAudioTo(Callee) Media Flow In State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
      });
      recHubPort.addMediaFlowOutStateChangedListener(new EventListener<MediaFlowOutStateChangedEvent>() {
        @Override
        public void onEvent(MediaFlowOutStateChangedEvent event) {
          log.info("[Composite recHubPort Media Flow Out State Changed Event] ::: [{}]", event.getState());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowOutStateChanged");
        }
      });
      compositeRecEP.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
        @Override
        public void onEvent(MediaFlowInStateChangedEvent event) {
          log.info("[compositeRecEP Media Flow In State Changed Event] ::: [{}]", event.getState());
          log.info("[compositeRecEP Media Flow In State Changed Event] ::: [{}]", event.getMediaType());
          JsonObject response = new JsonObject();
          response.addProperty("id", "mediaFlowInStateChanged");
        }
      });
      compositeRecEP.addRecordingListener(new EventListener<RecordingEvent>() {
        @Override
        public void onEvent(RecordingEvent event) {
          log.info("[compositeRecEP Recording Event] ::: recording");
          JsonObject response = new JsonObject();
          response.addProperty("id", "recording");
        }
      });
      compositeRecEP.addStoppedListener(new EventListener<StoppedEvent>() {
        @Override
        public void onEvent(StoppedEvent event) {
          log.info("[compositeRecEP Stopped Event] ::: stopped");
          JsonObject response = new JsonObject();
          response.addProperty("id", "stopped");
        }
      });
      compositeRecEP.addPausedListener(new EventListener<PausedEvent>() {
        @Override
        public void onEvent(PausedEvent event) {
          log.info("[compositeRecEP Paused Event] ::: paused");
          JsonObject response = new JsonObject();
          response.addProperty("id", "paused");
        }
      });

      webRtcCaller.connect(inHubPortFrom, MediaType.AUDIO);
      webRtcCaller.connect(inHubPortFrom, MediaType.VIDEO);

      webRtcCallee.connect(inHubPortTo, MediaType.AUDIO);
      webRtcCallee.connect(inHubPortTo, MediaType.VIDEO);

      recHubPort.connect(compositeRecEP, MediaType.VIDEO);
      recHubPort.connect(compositeRecEP, MediaType.AUDIO);

      //실험
      // inHubPortFrom.connect(compositeRecEP, MediaType.VIDEO);
      // inHubPortFrom.connect(compositeRecEP, MediaType.AUDIO);

      //mp3 파일
      webRtcCaller.connect(recorderAudioFrom, MediaType.AUDIO);
      webRtcCallee.connect(recorderAudioTo, MediaType.AUDIO);
      // webRtcCalleeRecv.connect(recorderAudioFrom, MediaType.AUDIO);
      // webRtcCallerRecv.connect(recorderAudioTo, MediaType.AUDIO);

    }else{
      
      // Connections
      webRtcCaller.connect(recorderCaller, MediaType.AUDIO);
      webRtcCaller.connect(recorderCaller, MediaType.VIDEO);

      webRtcCallee.connect(recorderCallee, MediaType.AUDIO);
      webRtcCallee.connect(recorderCallee, MediaType.VIDEO);

      // webRtcCalleeRecv.connect(recorderCaller, MediaType.AUDIO);
      // webRtcCalleeRecv.connect(recorderCaller, MediaType.VIDEO);
      // webRtcCallerRecv.connect(recorderCallee, MediaType.AUDIO);
      // webRtcCallerRecv.connect(recorderCallee, MediaType.VIDEO);
    }

    webRtcCaller.connect(webRtcCalleeRecv, MediaType.AUDIO);
    webRtcCaller.connect(webRtcCalleeRecv, MediaType.VIDEO);

    webRtcCallee.connect(webRtcCallerRecv, MediaType.AUDIO);
    webRtcCallee.connect(webRtcCallerRecv, MediaType.VIDEO);
    
  }

  public void record(Boolean isComposite) {
    if(isComposite){
      compositeRecEP.record();
      recorderAudioFrom.record();
      recorderAudioTo.record();
    }else{
      recorderCaller.record();
      recorderCallee.record();
    }
  }

  public void notFlowingVideo(UserSession user, Boolean isComposite){
    if(user.getWebRtcEndpoint() != null){
      if(user.getWebRtcEndpoint().equals(webRtcCallee)){
        user.getWebRtcEndpoint().disconnect(webRtcCallerRecv, MediaType.VIDEO);
        if(isComposite){
          user.getWebRtcEndpoint().disconnect(inHubPortTo, MediaType.VIDEO);
        }  
      }else{
        user.getWebRtcEndpoint().disconnect(webRtcCalleeRecv, MediaType.VIDEO);
        if(isComposite){
          user.getWebRtcEndpoint().disconnect(inHubPortFrom, MediaType.VIDEO);
        }
      }
    }
  }

  public void notFlowingAudio(UserSession user, Boolean isComposite){
    if(user.getWebRtcEndpoint() != null){
      if(user.getWebRtcEndpoint().equals(webRtcCallee)){
        user.getWebRtcEndpoint().disconnect(webRtcCallerRecv, MediaType.AUDIO);
        if(isComposite){
          user.getWebRtcEndpoint().disconnect(inHubPortTo, MediaType.AUDIO);
        }
      }else{
        user.getWebRtcEndpoint().disconnect(webRtcCalleeRecv, MediaType.AUDIO);
        if(isComposite){
          user.getWebRtcEndpoint().disconnect(inHubPortFrom, MediaType.AUDIO);
        }
      }
    }
  }

  public void flowingVideo(UserSession user, Boolean isComposite){
    if(user.getWebRtcEndpoint() != null){
      if(user.getWebRtcEndpoint().equals(webRtcCallee)){
        user.getWebRtcEndpoint().connect(webRtcCallerRecv, MediaType.VIDEO);
        if(isComposite){
          user.getWebRtcEndpoint().connect(inHubPortTo, MediaType.VIDEO);
        }
      }else{
        user.getWebRtcEndpoint().connect(webRtcCalleeRecv, MediaType.VIDEO);
        if(isComposite){
          user.getWebRtcEndpoint().connect(inHubPortFrom, MediaType.VIDEO);
        }
      }
    }
  }

  public void flowingAudio(UserSession user, Boolean isComposite){
    if(user.getWebRtcEndpoint() != null){
      if(user.getWebRtcEndpoint().equals(webRtcCallee)){
        user.getWebRtcEndpoint().connect(webRtcCallerRecv, MediaType.AUDIO);
        if(isComposite){
          user.getWebRtcEndpoint().connect(inHubPortTo, MediaType.AUDIO);
        }
      }else{
        user.getWebRtcEndpoint().connect(webRtcCalleeRecv, MediaType.AUDIO);
        if(isComposite){
          user.getWebRtcEndpoint().connect(inHubPortFrom, MediaType.AUDIO);
        }
      }
    }
  }
  
  public synchronized Composite createNewComposite(MediaPipeline pipeline, WebRtcEndpoint callerEndpoint, WebRtcEndpoint calleeEndpoint, String from, String to){ //shj
    Composite newComposite = new Composite.Builder(pipeline).build();
    HubPort newInHubPortFrom = new HubPort.Builder(newComposite).build();
    HubPort newInHubPortTo = new HubPort.Builder(newComposite).build();
    HubPort newRecHubPort = new HubPort.Builder(newComposite).build();
    RecorderEndpoint newRecEp = new RecorderEndpoint.Builder(pipeline, RECORDING_PATH + from + "-" + to + "-" + flag2 + RECORDING_EXT).withMediaProfile(MediaProfileSpecType.WEBM).build();
    
    this.inHubPortFrom = newInHubPortFrom;
    this.inHubPortTo = newInHubPortTo;
    this.recHubPort = newRecHubPort;
    this.compositeRecEP = newRecEp;

    newInHubPortFrom.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
      public void onEvent(MediaFlowInStateChangedEvent event) {
        log.info("[New Composite inHubPortFrom(Caller) Media Flow In State Changed Event] ::: [{}]", event.getState());
        JsonObject response = new JsonObject();
        response.addProperty("id", "mediaFlowInStateChanged");
      }
    });
    newInHubPortTo.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
      public void onEvent(MediaFlowInStateChangedEvent event) {
        log.info("[New Composite inHubPortTo(Callee) Media Flow In State Changed Event] ::: [{}]", event.getState());
        JsonObject response = new JsonObject();
        response.addProperty("id", "mediaFlowInStateChanged");
      }
    });
    newRecHubPort.addMediaFlowOutStateChangedListener(new EventListener<MediaFlowOutStateChangedEvent>() {
      @Override
      public void onEvent(MediaFlowOutStateChangedEvent event) {
        log.info("[New Composite recHubPort Media Flow Out State Changed Event] ::: [{}]", event.getState());
        JsonObject response = new JsonObject();
        response.addProperty("id", "mediaFlowOutStateChanged");
      }
    });
    
    callerEndpoint.connect(newInHubPortFrom, MediaType.AUDIO);
    callerEndpoint.connect(newInHubPortFrom, MediaType.VIDEO);

    calleeEndpoint.connect(newInHubPortTo, MediaType.AUDIO);
    calleeEndpoint.connect(newInHubPortTo, MediaType.VIDEO);

    newRecHubPort.connect(newRecEp, MediaType.AUDIO);
    newRecHubPort.connect(newRecEp, MediaType.VIDEO);

    newRecEp.record();

    return newComposite;
  }

  public synchronized HubPort createNewHub(HubPort inHubPort, Composite composite, WebRtcEndpoint inEp){ //shj
    inEp.disconnect(inHubPort, MediaType.AUDIO);
    inEp.disconnect(inHubPort, MediaType.VIDEO);
    inHubPort.release();
    HubPort newHub = new HubPort.Builder(composite).build();
    newHub.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
      public void onEvent(MediaFlowInStateChangedEvent event) {
        log.info("[Composite NEW inHubPort no.{} Media Flow In State Changed Event] ::: [{}]", count, event.getState());
        if(count <10){
          if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
            createNewHub(newHub, composite, inEp);
            count++;
            log.info("count ::: {}", count);
          }
        }
      }
    });
    inEp.connect(newHub, MediaType.AUDIO);
    inEp.connect(newHub, MediaType.VIDEO);
    log.info("New HubPort connected");
    return newHub;
  }

  public synchronized void createNewRecEpHub(RecorderEndpoint recEp, MediaPipeline pipeline, HubPort recHub, Composite composite, String from, String to, HubPort hp){ //shj
    recHub.disconnect(recEp, MediaType.AUDIO);
    recHub.disconnect(recEp, MediaType.VIDEO);
    recHub.release();
    recEp.release();
    flag2++;
    HubPort newHub = new HubPort.Builder(composite).build();
    RecorderEndpoint newRecEp = new RecorderEndpoint.Builder(pipeline, RECORDING_PATH + from + "-" + to + "-" + flag2 + RECORDING_EXT).withMediaProfile(MediaProfileSpecType.WEBM).build();
    hp.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
      public void onEvent(MediaFlowInStateChangedEvent event) {
        log.info("[Composite NEW recPort no.{} Media Flow In State Changed Event] ::: [{}]", count, event.getState());
        if(count <10){
          if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
           createNewRecEpHub(newRecEp, pipeline, newHub, composite, from, to, hp);
            count++;
            log.info("count ::: {}", count);
          }
        }
      }
    });
    log.info("New RecHub&RecorderEP connected");
    newHub.connect(newRecEp, MediaType.AUDIO);
    newHub.connect(newRecEp, MediaType.VIDEO);
    newRecEp.record();
  }


  public synchronized void createNewRecAudio(RecorderEndpoint recEp, MediaPipeline pipeline, WebRtcEndpoint inEp, String name){ //shj
    inEp.disconnect(recEp, MediaType.AUDIO);
    recEp.release();
    flag++;
    RecorderEndpoint newRecEp = new RecorderEndpoint.Builder(pipeline, AUDIO_PATH + name + "-" + flag + AUDIO_EXT).withMediaProfile(MediaProfileSpecType.MP4_AUDIO_ONLY).build();
    inEp.addMediaFlowInStateChangedListener(new EventListener<MediaFlowInStateChangedEvent>() {
      @Override
      public void onEvent(MediaFlowInStateChangedEvent event) {
        log.info("[NEW recorderAudio {} no.{} Media Flow In State Changed Event] ::: [{}]", name, count, event.getState());
        if(count <10){
          if(MediaFlowState.NOT_FLOWING.equals(event.getState())){ //shj
            count++;
            createNewRecAudio(newRecEp, pipeline, inEp, name);
            log.info("flag ::: {}", flag);
            log.info("count ::: {}", count);
          }
        }
      }
    });
    inEp.connect(newRecEp, MediaType.AUDIO);
    log.info("New RecorderEP(Audio Only) connected");
    newRecEp.record();
  }

  public synchronized void disconnectRecEp(RecorderEndpoint recEp,WebRtcEndpoint inEp,String name){
    inEp.disconnect(recEp, MediaType.AUDIO);
    recEp.release();
    count++;
    if(flag==0){
      flag++;
    }
  }

  public synchronized void disconnectRecEpAv(RecorderEndpoint recEp, HubPort hp){
    recEp.stopAndWait();
    hp.disconnect(recEp, MediaType.AUDIO);
    hp.disconnect(recEp, MediaType.VIDEO);
    recEp.release();
    count++;
    if(flag2==0){
      flag2++;
    }
  }

  public synchronized RecorderEndpoint connectRecEp(MediaPipeline pipeline, WebRtcEndpoint inEp, String name){
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    RecorderEndpoint newRecEp = new RecorderEndpoint.Builder(pipeline, AUDIO_PATH + name + "-" + flag + AUDIO_EXT).withMediaProfile(MediaProfileSpecType.MP4_AUDIO_ONLY).build();
    flag++;
    count++;
    inEp.connect(newRecEp, MediaType.AUDIO);
    log.info("New RecorderEP(Audio Only) connected");
    newRecEp.record();
    return newRecEp;
  }

  public synchronized RecorderEndpoint connectRecEpAv(MediaPipeline pipeline, HubPort hp, String from, String to){
    RecorderEndpoint newRecEp = new RecorderEndpoint.Builder(pipeline, RECORDING_PATH + from + "-" + to + "-" + flag2 + RECORDING_EXT).withMediaProfile(MediaProfileSpecType.WEBM).build();
    flag2++;
    count++;
    hp.connect(newRecEp, MediaType.AUDIO);
    hp.connect(newRecEp, MediaType.VIDEO);
    log.info("New RecorderEP(Video+Audio) connected");
    newRecEp.record();
    return newRecEp;
  }

  public void setWebRtcProfile(WebRtcEndpoint ep) {
    // 단위 : kbps
		ep.setMaxVideoSendBandwidth(max);
		ep.setMinVideoSendBandwidth(min);
		ep.setMaxVideoRecvBandwidth(max);
		ep.setMinVideoRecvBandwidth(min);
    // 단위 : bps
    // ep.setMinOuputBitrate(min);
    // ep.setMaxOuputBitrate(max);
	}

  

  public String generateSdpAnswerForCaller(String sdpOffer) {
    return webRtcCaller.processOffer(sdpOffer);
  }

  public String generateSdpAnswerForCallee(String sdpOffer) {
    return webRtcCallee.processOffer(sdpOffer);
  }

  public String generateSdpAnswerForCallerRecv(String sdpOffer) {
    return webRtcCallerRecv.processOffer(sdpOffer);
  }

  public String generateSdpAnswerForCalleeRecv(String sdpOffer) {
    return webRtcCalleeRecv.processOffer(sdpOffer);
  }

  public MediaPipeline getPipeline() {
    return pipeline;
  }

  public WebRtcEndpoint getCallerWebRtcEp() {
    return webRtcCaller;
  }

  public WebRtcEndpoint getCalleeWebRtcEp() {
    return webRtcCallee;
  }

  public WebRtcEndpoint getCallerWebRtcEpRecv() {
    return webRtcCallerRecv;
  }

  public WebRtcEndpoint getCalleeWebRtcEpRecv() {
    return webRtcCalleeRecv;
  }

  public RecorderEndpoint getCompositeRecEP(){
    return compositeRecEP;
  }

  public RecorderEndpoint getRecorderAudioFrom(){
    return recorderAudioFrom;
  }

  public RecorderEndpoint getRecorderAudioTo(){
    return recorderAudioTo;
  }

  public RecorderEndpoint getRecorderCaller(){
    return recorderCaller;
  }

  public RecorderEndpoint getRecorderCallee(){
    return recorderCallee;
  }

  public HubPort getInHubPortFrom(){
    return inHubPortFrom;
  }

  public HubPort getInHubPortTo(){
    return inHubPortTo;
  }

  public Composite getComposite(){
    return composite;
  }
}
