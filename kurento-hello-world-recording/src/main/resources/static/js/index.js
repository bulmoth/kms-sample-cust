/*
 * (C) Copyright 2014-2016 Kurento (http://kurento.org/)
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

var ws = new WebSocket('wss://' + location.host + '/recording');
var videoInput;
var videoOutput;
var webRtcPeer;
var state;

const NO_CALL = 0;
const IN_CALL = 1;
const POST_CALL = 2;
const DISABLED = 3;
const IN_PLAY = 4;
const PAUSED = 5; //shj

window.onload = function() {
	console = new Console();
	console.log('Page loaded ...');
	videoInput = document.getElementById('videoInput');
	videoOutput = document.getElementById('videoOutput');
	setState(NO_CALL);
}

window.onbeforeunload = function() {
	ws.close();
}

function setState(nextState) {
	switch (nextState) {
	case NO_CALL:
		$('#start').attr('disabled', false);
		$('#stop').attr('disabled', true);
		$('#play').attr('disabled', true);
		$('#pause').attr('disabled', true);
		break;
	case DISABLED:
		$('#start').attr('disabled', true);
		$('#stop').attr('disabled', true);
		$('#play').attr('disabled', true);
		$('#pause').attr('disabled', true);
		break;
	case IN_CALL:
		$('#start').attr('disabled', true);
		$('#stop').attr('disabled', false);
		$('#play').attr('disabled', true);
		$('#pause').attr('disabled', false);
		break;
	case POST_CALL:
		$('#start').attr('disabled', false);
		$('#stop').attr('disabled', true);
		$('#play').attr('disabled', false);
		$('#pause').attr('disabled', true);
		break;
	case IN_PLAY:
		$('#start').attr('disabled', true);
		$('#stop').attr('disabled', false);
		$('#play').attr('disabled', true);
		$('#pause').attr('disabled', true);
		break;	
	case PAUSED:
		$('#start').attr('disabled', true);
		$('#stop').attr('disabled', false);
		$('#play').attr('disabled', true);
		$('#pause').attr('disabled', true);
		break;
	default:
		onError('Unknown state ' + nextState);
	return;
	}
	state = nextState;
}

ws.onmessage = function(message) {
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
	case 'startResponse':
		startResponse(parsedMessage);
		// startResponseAudio(parsedMessage);
		// startResponseVideo(parsedMessage);
		break;
	case 'plusResponse':
		plusResponse(parsedMessage);
		break;
	case 'playResponse':
		playResponse(parsedMessage);
		break;
	case 'playEnd':
		playEnd();
		break;
	case 'error':
		setState(NO_CALL);
		onError('Error message from server: ' + parsedMessage.message);
		break;
	case 'iceCandidate':
		webRtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error)
				return console.error('Error adding candidate: ' + error);
		});
		// webRtcPeerAudio.addIceCandidate(parsedMessage.candidate, function(error) {
		// 	if (error)
		// 		return console.error('Error adding candidate: ' + error);
		// });
		// webRtcPeerVideo.addIceCandidate(parsedMessage.candidate, function(error) {
		// 	if (error)
		// 		return console.error('Error adding candidate: ' + error);
		// });
		break;
	case 'stopped':
		break;
	case 'paused':
		break;
	case 'recording':
		break;
	case 'iceCandidatePlus':
		webRtcPeerPlus.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error)
				return console.error('Error adding candidate: ' + error);
		});
		break;
	default:
		setState(NO_CALL);
	onError('Unrecognized message', parsedMessage);
	}
}

function start() {
	console.log('Starting video call ...');

	// Disable start button
	setState(DISABLED);
	showSpinner(videoInput, videoOutput);
	console.log('Creating WebRtcPeer and generating local sdp offer ...');

	var options = {
			localVideo : videoInput,
			remoteVideo : videoOutput,
			mediaConstraints : getConstraints(),
			onicecandidate : onIceCandidate
	}

	webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
			function(error) {
		if (error)
			return console.error(error);
		webRtcPeer.generateOffer(onOffer);
	});

	// webRtcPeerAudio = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
	// 		function(error) {
	// 	if (error)
	// 		return console.error(error);
	// 	webRtcPeerAudio.generateOffer(onOfferAudio);
	// });

	// webRtcPeerVideo = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
	// 		function(error) {
	// 	if (error)
	// 		return console.error(error);
	// 	webRtcPeerVideo.generateOffer(onOfferVideo);
	// });
}

function onOffer(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
			id : 'start',
			sdpOffer : offerSdp,
			mode :  $('input[name="mode"]:checked').val(),
			recordMode : $('input[name="recordMode"]:checked').val()
	}
	sendMessage(message);
}

// function onOfferAudio(error, offerSdp) {
// 	if (error)
// 		return console.error('Error generating the offer');
// 	console.info('Invoking SDP offer callback function ' + location.host);
// 	var message = {
// 			id : 'start',
// 			sdpOfferAudio : offerSdp,
// 			mode :  $('input[name="mode"]:checked').val(),
// 			recordMode : $('input[name="recordMode"]:checked').val()
// 	}
// 	sendMessage(message);
// }

// function onOfferVideo(error, offerSdp) {
// 	if (error)
// 		return console.error('Error generating the offer');
// 	console.info('Invoking SDP offer callback function ' + location.host);
// 	var message = {
// 			id : 'start',
// 			sdpOfferVideo : offerSdp,
// 			mode :  $('input[name="mode"]:checked').val(),
// 			recordMode : $('input[name="recordMode"]:checked').val()
// 	}
// 	sendMessage(message);
// }

// function onError(error) {
// 	console.error(error);
// }

function onIceCandidate(candidate) {
	console.log('Local candidate' + JSON.stringify(candidate));

	var message = {
			id : 'onIceCandidate',
			candidate : candidate,
			recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(message);
}

function startResponse(message) {
	setState(IN_CALL);
	console.log('SDP answer received from server. Processing ...');

	webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
		if (error)
			return console.error(error);
	});
}

// function startResponseAudio(message) {
// 	setState(IN_CALL);
// 	console.log('AudioSDP answer received from server. Processing ...');

// 	webRtcPeerAudio.processAnswer(message.sdpAnswerAudio, function(error) {
// 		if (error)
// 			return console.error(error);
// 	});
// }

// function startResponseVideo(message) {
// 	setState(IN_CALL);
// 	console.log('VideoSDP answer received from server. Processing ...');

// 	webRtcPeerVideo.processAnswer(message.sdpAnswerVideo, function(error) {
// 		if (error)
// 			return console.error(error);
// 	});
// }

function stop() {
	var stopMessageId = (state == IN_CALL) ? 'stop' : 'stopPlay';
	console.log('Stopping video while in ' + state + '...');
	setState(POST_CALL);
	if (webRtcPeer /*|| webRtcPeerAudio || webRtcPeerVideo*/ || webRtcPeerPlus) {
		webRtcPeer.dispose();
		// webRtcPeerAudio.dispose();
		// webRtcPeerVideo.dispose();
		webRtcPeer = null;
		// webRtcPeerAudio = null;
		// webRtcPeerVideo = null;

		webRtcPeerPlus.dispose();
		webRtcPeerPlus = null;

		var message = {
				id : stopMessageId,
				recordMode : $('input[name="recordMode"]:checked').val()
		}
		sendMessage(message);
	}
	hideSpinner(videoInput, videoOutput);
}

function play() {
	console.log("Starting to play recorded video...");

	// Disable start button
	setState(DISABLED);
	showSpinner(videoOutput);

	console.log('Creating WebRtcPeer and generating local sdp offer ...');

	var options = {
			remoteVideo : videoOutput,
			mediaConstraints : getConstraints(),
			onicecandidate : onIceCandidate
	}

	webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
			function(error) {
		if (error)
			return console.error(error);
		webRtcPeer.generateOffer(onPlayOffer);
	});
}

function onPlayOffer(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
			id : 'play',
			sdpOffer : offerSdp,
			recordMode : $('input[name="recordMode"]:checked').val()
	}
	sendMessage(message);
}

function getConstraints() {
	var mode = $('input[name="mode"]:checked').val();
	var constraints = {
			audio : true,
			video : {
				width : 1280 //shj
			}
	}

	if (mode == 'video-only') {
		constraints.audio = false;
	} else if (mode == 'audio-only') {
		constraints.video = false;
	}
	
	return constraints;
}


function playResponse(message) {
	setState(IN_PLAY);
	webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
		if (error)
			return console.error(error);
	});
}

function playEnd() {
	setState(POST_CALL);
	hideSpinner(videoInput, videoOutput);
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Sending message: ' + jsonMessage);
	ws.send(jsonMessage);
}

function showSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].poster = './img/transparent-1px.png';
		arguments[i].style.background = "center transparent url('./img/spinner.gif') no-repeat";
	}
}

function hideSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].src = '';
		arguments[i].poster = './img/webrtc.png';
		arguments[i].style.background = '';
	}
}
/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function(event) {
	event.preventDefault();
	$(this).ekkoLightbox();
});


//shj
function pause(){
	var pauseMessageId = (state != PAUSED) ? 'pause' : 'resume';
	console.log('Changing video state...');
	if( pauseMessageId == "pause"){
		setState(PAUSED);
		var message = {
				id : pauseMessageId,
				recordMode : $('input[name="recordMode"]:checked').val()
		}
		sendMessage(message);
		hideSpinner(videoInput, videoOutput);
		$('#pause').removeClass('btn-primary');
	}else{
		setState(IN_CALL);
		var message = {
				id : pauseMessageId,
				recordMode : $('input[name="recordMode"]:checked').val()
		}
		sendMessage(message);
		hideSpinner(videoInput, videoOutput);
		$('#pause').addClass('btn-primary');
	}
}

function stopNwait(){
	var messageId = 'stopNwait';
	console.log('Stop and Wait Video...');
	setState(POST_CALL);
	if (webRtcPeer /*|| webRtcPeerAudio || webRtcPeerVideo*/ || webRtcPeerPlus) {
		webRtcPeer.dispose();
		// webRtcPeerAudio.dispose();
		// webRtcPeerVideo.dispose();
		webRtcPeer = null;
		// webRtcPeerAudio = null;
		// webRtcPeerVideo = null;
		webRtcPeerPlus.dispose();
		webRtcPeerPlus = null;

		var message = {
				id : messageId,
				recordMode : $('input[name="recordMode"]:checked').val()
		}
		sendMessage(message);
	}
	hideSpinner(videoInput, videoOutput);
}


var webRtcPeerPlus;

function plus(){

	var options = {
		localVideo : videoInput,
		remoteVideo : videoOutput,
		mediaConstraints : getConstraints(),
		onicecandidate : onIceCandidate
	}

	webRtcPeerPlus = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
			function(error) {
		if (error)
			return console.error(error);
		webRtcPeerPlus.generateOffer(onOfferPlus);
	});

}

function onOfferPlus(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
			id : 'plus',
			sdpOffer : offerSdp,
			mode :  $('input[name="mode"]:checked').val(),
			recordMode : $('input[name="recordMode"]:checked').val()
	}
	sendMessage(message);
}

function plusResponse(message){
	setState(IN_CALL);
	console.log('SDP answer received from server. Processing ...');

	webRtcPeerPlus.processAnswer(message.sdpAnswer, function(error) {
		if (error)
			return console.error(error);
	});
}

function disconnect(){

	if(webRtcPeer || webRtcPeerPlus){
		webRtcPeer.dispose();
		webRtcPeerPlus.dispose();
		webRtcPeer = null;
		webRtcPeerPlus = null;
	
		console.log("disconnecting...");
	}
	var message={
		id : "disconnect",
		mode :  $('input[name="mode"]:checked').val(),
		recordMode : $('input[name="recordMode"]:checked').val()
	}	

	sendMessage(message);


}