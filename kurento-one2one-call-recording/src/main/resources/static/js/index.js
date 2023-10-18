/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
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

var ws = new WebSocket('wss://' + location.host + '/call');
var videoInput;
var videoOutput;
var webRtcPeer = null;
var webRtcPeerRecv = null;
var from;
var connection;
var videoId;
var audioId;
var isCalled = false;
var isRtp = false;

var registerName = null;
var registerState = null;
const NOT_REGISTERED = 0;
const REGISTERING = 1;
const REGISTERED = 2;

function setRegisterState(nextState) {
	switch (nextState) {
	case NOT_REGISTERED:
		enableButton('#register', 'register()');
		setCallState(DISABLED);
		break;
	case REGISTERING:
		disableButton('#register');
		break;
	case REGISTERED:
		disableButton('#register');
		setCallState(NO_CALL);
		break;
	default:
		return;
	}
	registerState = nextState;
}

var callState = null;
const NO_CALL = 0;
const IN_CALL = 1;
const POST_CALL = 2;
const DISABLED = 3;
const IN_PLAY = 4;
const PAUSED = 5;
const NOT_FLOWING = 6;

function setCallState(nextState) {
	switch (nextState) {
	case NO_CALL:
		enableButton('#call', 'call()');
		// disableButton('#terminate');
		// disableButton('#play');

		enableButton('#terminate');
		enableButton('#play');
		break;
	case DISABLED:
		// disableButton('#call');
		// disableButton('#terminate');
		// disableButton('#play');

		enableButton('#call');
		enableButton('#terminate');
		enableButton('#play');
		break;
	case POST_CALL:
		enableButton('#call', 'call()');
		// disableButton('#terminate');
		enableButton('#play', 'play()');

		enableButton('#terminate');
		break;
	case IN_CALL:
	case IN_PLAY:
		// disableButton('#call');
		enableButton('#terminate', 'stop()');
		// disableButton('#play');

		enableButton('#call');
		enableButton('#play');
		break;
	case PAUSED:
		break;
	case NOT_FLOWING:
		break;
	default:
		return;
	}
	callState = nextState;
}

function disableButton(id) {
	$(id).attr('disabled', true);
	$(id).removeAttr('onclick');
}

function enableButton(id, functionName) {
	$(id).attr('disabled', false);
	$(id).attr('onclick', functionName);
}

window.onload = function() {
	console = new Console();
	setRegisterState(NOT_REGISTERED);
	var drag = new Draggabilly(document.getElementById('videoSmall'));
	videoInput = document.getElementById('videoInput');
	videoOutput = document.getElementById('videoOutput');
	document.getElementById('name').focus();
	// navigator.mediaDevices.enumerateDevices().then((devices)=>{
	// 	devices.forEach((device)=>{
	// 		if(device.kind == 'videoinput'){
	// 			videoId = device.deviceId;
	// 		}else if(device.kind == 'audioinput'){
	// 			audioId = device.deviceId;
	// 		}
	// 	});
	// });
}

window.onbeforeunload = function() {
	ws.close();
}

ws.onmessage = function(message) {
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
	case 'registerResponse':
		registerResponse(parsedMessage);
		break;
	case 'callResponse':
		callResponse(parsedMessage);
		break;
	case 'incomingCall':
		incomingCall(parsedMessage);
		break;
	case 'incomingCall2':
		console.log('안녕');
		incomingCall2(parsedMessage);
	break;
	case 'startCommunication':
		startCommunication(parsedMessage);
		break;
	case 'startCommunicationRecv':
		console.log('안녕222');
		startCommunicationRecv(parsedMessage);
		break;
	case 'stopCommunication':
		console.info('Communication ended by remote peer');
		stop(true);
		break;
	case 'playResponse':
		playResponse(parsedMessage);
		break;
	case 'playEnd':
		playEnd();
		break;
	case 'iceCandidate':
		webRtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error)
				return console.error('Error adding candidate: ' + error);
		});
		break;
	case 'iceCandidateRecv':
		webRtcPeerRecv.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error)
				return console.error('Error adding candidate: ' + error);
		});
	break;
	case 'iceCandidateOutHub':
		webRtcPeerOutHub.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error)
				return console.error('Error adding candidate: ' + error);
		});
	break;
	case 'startOutHubPlay':
		webRtcPeerOutHub.processAnswer(parsedMessage.sdpAnswer, function(error) {
			if (error)
				return console.error(error);
		});
	break;
	case 'plus':
		plusResponse();
		break;
	case 'reAnswer':
	 	reAnswerResponse();
	break;
	case 'makeUrl':
	 	makeUrl(parsedMessage);
	break;
	default:
		console.error('Unrecognized message', parsedMessage);
	}
}

function registerResponse(message) {
	if (message.response == 'accepted') {
		setRegisterState(REGISTERED);
		document.getElementById('peer').focus();
	} else {
		setRegisterState(NOT_REGISTERED);
		var errorMessage = message.response ? message.response
				: 'Unknown reason for register rejection.';
		console.log(errorMessage);
		document.getElementById('name').focus();
		alert('Error registering user. See console for further information.');
	}
}

function callResponse(message) {
	if (message.response != 'accepted') {
		console.info('Call not accepted by peer. Closing call');
		stop();
		setCallState(NO_CALL);
		if (message.message) {
			alert(message.message);
		}
	} else {
		setCallState(IN_CALL);
		webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
			if (error)
				return console.error(error);
		});
	}
}

function startCommunicationRecv(message) {
	console.log("확인11111", isCalled);
	setCallState(IN_CALL);

	console.log("확인222222", message.sdpAnswer);
	
	webRtcPeerRecv.processAnswer(message.sdpAnswer, function(error) {
		console.log("확인33333", error);
		if (error)
			return console.error(error);
	});

	console.log("확인444444", isCalled);

	//shj
	if(webRtcPeer == null && !isRtp){	//ios에서 실행되어버림
		var options = {
			localVideo : document.getElementById('videoInput'),
			//remoteVideo : document.getElementById('videoOutput'),
			onicecandidate : onIceCandidate
			// mediaConstraints : {
			// 	audio : {
			// 		deviceId : {
			// 			exact : audioId
			// 		}
			// 	},
			// 	video : {
			// 		deviceId : {
			// 			exact : videoId
			// 		}
			// 	}
			// }
		}
		webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
				function(error) {
					if (error) {
						return console.error(error);
					}
					this.generateOffer(onOfferCall2);
				});
	}else if(webRtcPeer == null && isRtp){
		var options = {
			localVideo : null,
			videoStream : "rtp://192.168.0.218:5004",
			onicecandidate : onIceCandidate
		}
		webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
			function(error) {
				if (error) {
					return console.error(error);
				}
				this.generateOffer(onOfferCall2);
			});
	}
}

function startCommunication(message) {
	setCallState(IN_CALL);
	webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
		if (error)
			return console.error(error);
	});
}

function playResponse(message) {
	if (message.response != 'accepted') {
		hideSpinner(videoOutput);
		document.getElementById('videoSmall').style.display = 'block';
		alert(message.error);
		document.getElementById('peer').focus();
		setCallState(POST_CALL);
	} else {
		setCallState(IN_PLAY);
		document.getElementById('httpUrl').value = message.httpUrl;
		webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
			if (error)
				return console.error(error);
		});
	}
}

function incomingCall(message) {
	// If bussy just reject without disturbing user
	// if (callState != NO_CALL && callState != POST_CALL) {
	// 	var response = {
	// 		id : 'incomingCallResponse',
	// 		from : message.from,
	// 		to : message.to, //shj
	// 		callResponse : 'reject',
	// 		message : 'bussy',
	// 		recordMode : $('input[name="recordMode"]:checked').val()
	// 	};
	// 	return sendMessage(response);
	// }

	setCallState(DISABLED);
	if (confirm('User ' + message.from
			+ ' is calling you. Do you accept the call?')) {
		showSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));

		from = message.from;
		to = message.to;
		var options = {
			//localVideo : document.getElementById('videoInput'),
			remoteVideo : document.getElementById('videoOutput'),
			onicecandidate : onIceCandidate
			// mediaConstraints : {
			// 	audio : {
			// 		deviceId : {
			// 			exact : audioId
			// 		}
			// 	},
			// 	video : {
			// 		deviceId : {
			// 			exact : videoId
			// 		}
			// 	}
			// }
		}
		webRtcPeerRecv = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
				function(error) {
					if (error) {
						return console.error(error);
					}
					this.generateOffer(onOfferIncomingCall);
				});
	} else {
		var response = {
			id : 'incomingCallResponse',
			from : message.from,
			to : message.to, //shj
			callResponse : 'reject',
			message : 'user declined',
			recordMode : $('input[name="recordMode"]:checked').val()
		};
		sendMessage(response);
		stop();
	}
}

function incomingCall2(message) {
	// If bussy just reject without disturbing user
	// if (callState != NO_CALL && callState != POST_CALL) {
	// 	var response = {
	// 		id : 'incomingCallResponse',
	// 		from : message.from,
	// 		to : message.to, //shj
	// 		callResponse : 'reject',
	// 		message : 'bussy',
	// 		recordMode : $('input[name="recordMode"]:checked').val()
	// 	};
	// 	return sendMessage(response);
	// }

	setCallState(DISABLED);
	if (confirm('User ' + message.from
			+ ' is calling you. Do you accept the call?')) {
		showSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));

		from = message.from;
		to = message.to;
		var options = {
			//localVideo : document.getElementById('videoInput'),
			remoteVideo : document.getElementById('videoOutput'),
			onicecandidate : onIceCandidate
			// mediaConstraints : {
			// 	audio : {
			// 		deviceId : {
			// 			exact : audioId
			// 		}
			// 	},
			// 	video : {
			// 		deviceId : {
			// 			exact : videoId
			// 		}
			// 	}
			// }
		}
		webRtcPeerRecv = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
				function(error) {
					if (error) {
						return console.error(error);
					}
					this.generateOffer(onOfferIncomingCall2);
				});
	} else {
		var response = {
			id : 'incomingCallResponse',
			from : message.from,
			to : message.to, //shj
			callResponse : 'reject',
			message : 'user declined',
			recordMode : $('input[name="recordMode"]:checked').val()
		};
		sendMessage(response);
		stop();
	}
}


function onOfferIncomingCall(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer ' + error);
	var response = {
		id : 'incomingCallResponse',
		from : from,
		to : to, //shj
		callResponse : 'accept',
		sdpOffer : offerSdp,
		recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(response);
}

function onOfferIncomingCall2(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer ' + error);
	var response = {
		id : 'incomingCallResponse2',
		from : from,
		to : to, //shj
		callResponse : 'accept',
		sdpOffer : offerSdp,
		recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(response);
}

function register() {
	var name = document.getElementById('name').value;
	if (name == '') {
		window.alert('You must insert your user name');
		document.getElementById('name').focus();
		return;
	}
	setRegisterState(REGISTERING);

	var message = {
		id : 'register',
		name : name,
		recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(message);
}

function call() {
	if (document.getElementById('peer').value == '') {
		document.getElementById('peer').focus();
		window.alert('You must specify the peer name');
		return;
	}
	setCallState(DISABLED);
	isCalled = true;
	showSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));

	var options = {
		localVideo : document.getElementById('videoInput'),
		//remoteVideo : document.getElementById('videoOutput'),
		onicecandidate : onIceCandidate
		// mediaConstraints : {
		// 	audio : {
		// 		deviceId : {
		// 			exact : audioId
		// 		}
		// 	},
		// 	video : {
		// 		deviceId : {
		// 			exact : videoId
		// 		}
		// 	}
		// }
	}
	webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options, //iOS Safari에서는 WebRtcPeer의 constructor 실행 불가(RTCPeerConnection.getLocalstreams 사용)
			function(error) {
				if (error) {
					return console.error(error);
				}
				this.generateOffer(onOfferCall);
			});
}

function onOfferCall(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer ' + error);
	console.log('Invoking SDP offer callback function');
	var message = {
		id : 'call',
		from : document.getElementById('name').value,
		to : $('#peer').val(),
		sdpOffer : offerSdp,
		recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(message);
}

function onOfferCall2(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer ' + error);
	console.log('Invoking SDP offer callback function');
	var message = {
		id : 'call2',
		from : $('#peer').val(),
		to : document.getElementById('name').value,
		sdpOffer : offerSdp,
		recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(message);
}

function onOfferRtpCall(error, offerSdp){
	if(error) return console.error('Error generating the offer ' + error);
	console.log('Invoking SDP offer callback function');
	var message = {
		id : 'rtpCall',
		from : document.getElementById('name').value,
		to : $('#peer').val(),
		sdpOffer : offerSdp,
		recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(message);
}

function play() {
	var peer = document.getElementById('peer').value;
	if (peer == '') {
		window
				.alert("You must insert the name of the user recording to be played (field 'Peer')");
		document.getElementById('peer').focus();
		return;
	}

	//document.getElementById('videoSmall').style.display = 'none';
	//setCallState(DISABLED);
	// showSpinner(document.getElementById('videoOutput'));

	var options = {
		remoteVideo : document.getElementById('video'),
		onicecandidate : onIceCandidate
	}
	webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
			function(error) {
				if (error) {
					return console.error(error);
				}
				this.generateOffer(onOfferPlay);
			});
}

function onOfferPlay(error, offerSdp) {
	console.log('Invoking SDP offer callback function');
	var message = {
		id : 'play',
		user : document.getElementById('peer').value,
		sdpOffer : offerSdp,
		recordMode : $('input[name="recordMode"]:checked').val(),
		url : document.getElementById('videourl').value
	};
	sendMessage(message);
}

function playEnd() {
	setCallState(POST_CALL);
	hideSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));
	document.getElementById('videoSmall').style.display = 'block';
}

function stop(message) {
	var stopMessageId = (callState == IN_CALL) ? 'stop' : 'stopPlay';
	setCallState(POST_CALL);
	if (webRtcPeer) {
		webRtcPeer.dispose();
		webRtcPeer = null;

		webRtcPeerRecv.dispose();
		webRtcPeerRecv = null;

		if (!message) {
			var message = {
				id : stopMessageId,
				recordMode : $('input[name="recordMode"]:checked').val()
			}
			sendMessage(message);
		}
	}
	hideSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));
	document.getElementById('videoSmall').style.display = 'block';
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Sending message: ' + jsonMessage);
	ws.send(jsonMessage);
	console.log('웹소켓보냄');
}

function onIceCandidate(candidate) {
	console.log('Local candidate ' + JSON.stringify(candidate));

	var message = {
		id : 'onIceCandidate',
		candidate : candidate,
		recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(message);
}

function showSpinner() {
	for (var i = 0; i < arguments.length; i++) {
		arguments[i].poster = './img/transparent-1px.png';
		arguments[i].style.background = 'center transparent url("./img/spinner.gif") no-repeat';
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
function plus(){
	// if (document.getElementById('peer').value == '') {
	// 	document.getElementById('peer').focus();
	// 	window.alert('You must specify the peer name');
	// 	return;
	// }
	// setCallState(DISABLED);
	// showSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));

	// var options = {
	// 	localVideo : document.getElementById('videoInput'),
	// 	remoteVideo : document.getElementById('videoOutput'),
	// 	onicecandidate : onIceCandidate,
	// 	mediaConstraints : {
	// 		audio : true,
	// 		video : true
	// 	}
	// }
	// webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
	// 		function(error) {
	// 			if (error) {
	// 				return console.error(error);
	// 			}
	// 			this.generateOffer(onOfferCall);
	// 		});
}

function plusResponse(){

}

function pause(){
	var pauseMsgId = (callState != PAUSED) ? 'pause' : 'resume';
	console.log('Changing video state...');
	if( pauseMsgId == "pause"){
		setCallState(PAUSED);
		var message = {
				id : pauseMsgId,
				recordMode : $('input[name="recordMode"]:checked').val()
		}
		sendMessage(message);
		hideSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));
		$('#pause').removeClass('btn-secondary');
	}else{
		setCallState(IN_CALL);
		var message = {
				id : pauseMsgId,
				recordMode : $('input[name="recordMode"]:checked').val()
		}
		sendMessage(message);
		hideSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));
		$('#pause').addClass('btn-secondary');
	}
}

function clean(){
	var message = {
		id : 'clean',
		recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(message);
}

function release(){
	var message = {
		id : 'release',
		recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(message);
}

var videoTrack;
function notFlowingVideo(){
	var pauseMsgId = (callState != NOT_FLOWING) ? 'notFlowingVideo' : 'flowingVideo';
	console.log('Changing Video flowing state...');
	if( pauseMsgId == "notFlowingVideo"){
		videoTrack = null;
		setCallState(NOT_FLOWING);
		// webRtcPeer.videoEnabled = false;
		// webRtcPeer.getLocalStream().getVideoTracks().forEach(function(track) {
		// 	track.enabled = false
		// });
		// var message = {
		// 	id : pauseMsgId,
		// 	recordMode : $('input[name="recordMode"]:checked').val()
		// }
		// sendMessage(message);
		// webRtcPeer.getLocalStream().getVideoTracks().forEach(function(track) {
		// 	track.stop();
		// });
		// webRtcPeer.getLocalStream().getVideoTracks().forEach(function(track) {
		// 	videoTrack = track.clone();
		// 	track.stop();
		// 	webRtcPeer.getLocalStream().removeTrack(track);
		// });
		webRtcPeer.peerConnection.getSenders().forEach((sender)=>{
			if(sender.track.kind == 'video'){
				videoTrack = sender.track.clone();
				sender.track.stop();
			}
		});
		hideSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));
		$('#notFlowingVideo').removeClass('btn-info');
	}else{
		setCallState(IN_CALL);
		// webRtcPeer.videoEnabled = true;
		// webRtcPeer.getLocalStream().getVideoTracks().forEach(function(track) {
		// 	track.enabled = true
		// });

		// var message = {
		// 	id : 'reOffer',
		// 	from : document.getElementById('name').value,
		// 	to : $('#peer').val(),
		// 	recordMode : $('input[name="recordMode"]:checked').val()
		// }
		// sendMessage(message);
		// webRtcPeer.getLocalStream().addTrack(videoTrack);
		webRtcPeer.peerConnection.getSenders().forEach((sender)=>{
			if(sender.track.kind == 'video'){
				sender.replaceTrack(videoTrack);
			}
		});
		//webRtcPeer.generateOffer(reOfferCall);
		console.log(videoTrack);
		// console.log(webRtcPeer.getLocalStream());
		// console.log(webRtcPeer.getLocalStream().getVideoTracks());
		hideSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));
		$('#notFlowingVideo').addClass('btn-info');
	}
}

var audioTrack;
function notFlowingAudio(){
	var pauseMsgId = (callState != NOT_FLOWING) ? 'notFlowingAudio' : 'flowingAudio';
	console.log('Changing Audio flowing state...');
	if( pauseMsgId == "notFlowingAudio"){
		audioTrack = null;
		setCallState(NOT_FLOWING);
		// webRtcPeer.audioEnabled = false;
		// webRtcPeer.getLocalStream().getAudioTracks().forEach(function(track) {
		// 	track.enabled = false
		// });
		// var message = {
		// 	id : pauseMsgId,
		// 	recordMode : $('input[name="recordMode"]:checked').val()
		// }
		// sendMessage(message);
		// webRtcPeer.getLocalStream().getAudioTracks().forEach(function(track) {
		// 	track.stop();
		// });
		// webRtcPeer.getLocalStream().getAudioTracks().forEach(function(track) {
		// 	audioTrack = track.clone();
		// 	track.stop();
		// 	webRtcPeer.getLocalStream().removeTrack(track);
		// });
		webRtcPeer.peerConnection.getSenders().forEach((sender)=>{
			if(sender.track.kind == 'audio'){
				audioTrack = sender.track.clone();
				sender.track.stop();
			}
		});
		hideSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));
		$('#notFlowingAudio').removeClass('btn-warning');
	}else{
		setCallState(IN_CALL);
		// webRtcPeer.audioEnabled = true;
		// webRtcPeer.getLocalStream().getAudioTracks().forEach(function(track) {
		// 	track.enabled = true
		// });
		// var message = {
		// 	id : pauseMsgId,
		// 	recordMode : $('input[name="recordMode"]:checked').val()
		// }
		// sendMessage(message);
		// webRtcPeer.getLocalStream().addTrack(audioTrack);
		webRtcPeer.peerConnection.getSenders().forEach((sender)=>{
			if(sender.track.kind == 'audio'){
				sender.replaceTrack(audioTrack);
			}
		});
		//webRtcPeer.generateOffer(reOfferCall);
		// console.log(webRtcPeer.getLocalStream());
		// console.log(webRtcPeer.getLocalStream().getAudioTracks());
		hideSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));
		$('#notFlowingAudio').addClass('btn-warning');
	}
}

function reOfferCall(error, offerSdp){
	if (error)
		return console.error('Error generating the offer ' + error);
	console.log('Invoking SDP offer callback function');
	var message = {
		id : 'reOffer',
		from : document.getElementById('name').value,
		to : $('#peer').val(),
		sdpOffer : offerSdp,
		recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(message);
}

// var track;
// function reAnswerResponse(message) {
// 	// webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
// 	// 	if (error)
// 	// 		return console.error(error);
// 	// });
// 	// track = JSON.parse(message.track);
// 	track = webRtcPeerRecv.getLocalStream().getVideoTracks()[0];
// 	console.log('local Stream video track :::');
// 	console.log(track);
// 	console.log('remote Stream video track :::');
// 	console.log(webRtcPeerRecv.getRemoteStream().getVideoTracks()[0]);
// 	webRtcPeerRecv.getRemoteStream().addTrack(track);
// }

function concat(){
	var message = {
		id : 'concat',
		recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(message);
}

function newRecEp(){
	var message = {
		id : 'newRecEp',
		recordMode : $('input[name="recordMode"]:checked').val()
	};
	sendMessage(message);
}

function makeUrl(message){
	document.getElementById('videourl').value = message.videoUrl;
}

function makeRtpConn(){
	if (document.getElementById('peer').value == '') {
		document.getElementById('peer').focus();
		window.alert('You must specify the peer name');
		return;
	}
	setCallState(DISABLED);
	isCalled = true;
	isRtp = true;
	showSpinner(document.getElementById('videoInput'), document.getElementById('videoOutput'));

	var options = {
		remoteVideo : document.getElementById('videoRtp'),
		onicecandidate : onIceCandidate
	}
	webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
		function(error) {
			if (error) {
				return console.error(error);
			}
			this.generateOffer(onOfferCall);
		});
}

function outHubPlay() {
	var peer = document.getElementById('peer').value;
	if (peer == '') {
		window
				.alert("You must insert the name of the user recording to be played (field 'Peer')");
		document.getElementById('peer').focus();
		return;
	}

	var options = {
		remoteVideo : document.getElementById('videoRtp'),
		onicecandidate : onIceCandidate
	}
	webRtcPeerOutHub = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
			function(error) {
				if (error) {
					return console.error(error);
				}
				this.generateOffer(onOfferOutHubPlay);
			});
}

function onOfferOutHubPlay(error, offerSdp) {
	console.log('Invoking SDP offer callback function');
	var message = {
		id : 'outHubPlay',
		from : document.getElementById('name').value,
		to : $('#peer').val(),
		sdpOffer : offerSdp,
		recordMode : $('input[name="recordMode"]:checked').val(),
	};
	sendMessage(message);
}