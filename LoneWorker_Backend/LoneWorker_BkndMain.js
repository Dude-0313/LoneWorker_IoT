// LoneWorker Backend Main

var mqtt = require('mqtt')
//var mqttClient  = mqtt.connect('mqtt://test.mosquitto.org')
var mqttClient  = mqtt.connect('mqtt://localhost')
var dweetio= require("node-dweetio"); // http://www.dweet.io/follow/LONE_WORKER
var dweetClient = new dweetio();

var MongoDBClient = require("mongodb").MongoClient;
var mongodbURI='mongodb://localhost:27017/LoneWorkerDB'

var TOKEN_TIMESTAMP = "loneworker/timestamp";
var TOKEN_HEARTRATE = "loneworker/heartrate";
var TOKEN_LOCATION_LAT = "loneworker/location/lat";
var TOKEN_LOCATION_LONG = "loneworker/location/long";
var TOKEN_MOVEMENT = "loneworker/movement";
var TOKEN_ALERT = "loneworker/alert";
var TOKEN_PEOPLE_COUNT = "loneworker/peoplecount";
var HEART_RATE_THRESHOLD = 20;
var ALONE_MINS_THRESHOLD = 15;
var MOVEMENT_MINS_THRESHOLD = 15;
var PEOPLE_THRESHOLD = 1

var tokens = [];
var timestamp;
var heartrate=0;
var prev_location_ts = Date.now();
var prev_location_lat = 0.0;
var prev_location_long = 0.0;
var prev_movement_ts= Date.now();
var prev_movement= "true";
var people_count_ts = Date.now();
var init=false
var peoplecount=0;


mqttClient.on('connect', function () {
  console.log('Conneted to MQTT Broker')
  mqttClient.subscribe(TOKEN_TIMESTAMP)
  mqttClient.subscribe(TOKEN_HEARTRATE)
  mqttClient.subscribe(TOKEN_LOCATION_LAT)
  mqttClient.subscribe(TOKEN_LOCATION_LONG)
  mqttClient.subscribe(TOKEN_MOVEMENT)
  mqttClient.subscribe(TOKEN_PEOPLE_COUNT)
})
 
mqttClient.on('message', function (topic, message) {
  // message is Buffer 
  console.log(message.toString())
  handleToken(topic,message)
  if(topic!=TOKEN_TIMESTAMP){
    sendDweet()
  }
  // mqttClient.end()
})

function handleToken(topic, message) {
    console.log('Message Received :')
    console.log('Topic :' + topic)
    console.log('Payload' + message)
    name = message.toString().split(";");
    switch(topic){
        case TOKEN_TIMESTAMP :
            console.log("TimeStamp : "+ message )
            timestamp = new Date(Date.parse(message));
            if(init==false){
                prev_location_ts = timestamp
                prev_movement_ts = timestamp
                people_count_ts = timestamp
                init=true
                console.log("init timestamp"+timestamp.toString())
            }
            break;   
        case TOKEN_HEARTRATE :
            console.log("HeartRate : "+message )
            heartrate = Number(message);
            if(heartrate < HEART_RATE_THRESHOLD) generateAlert(TOKEN_HEARTRATE)
            break;   
        case TOKEN_LOCATION_LAT :
            console.log("Location Lat :"+message)
            if(prev_location_lat != Number(message)) {
                prev_location_lat= Number(message)
                prev_location_ts = timestamp;
            }
            else if(minsPastSince(timestamp,prev_movement_ts)> MOVEMENT_MINS_THRESHOLD){
                generateAlert(TOKEN_LOCATION_LAT)
            }
            break;  
            break;             
        case TOKEN_LOCATION_LONG :
            console.log("Location Long :"+message)
            if(prev_location_long != Number(message)) {
                prev_location_long = Number(message)
                prev_location_ts = timestamp;
            }
            else if(minsPastSince(timestamp,prev_movement_ts)> MOVEMENT_MINS_THRESHOLD){
                generateAlert(TOKEN_LOCATION_LONG)
            }
            break;  
            break;             
        case TOKEN_MOVEMENT :
            console.log("Movement :"+message)
            if(prev_movement != message) {
                prev_movement = message
                prev_movement_ts = timestamp
            }
            else if(minsPastSince(timestamp,prev_movement_ts)> MOVEMENT_MINS_THRESHOLD){
                generateAlert(TOKEN_MOVEMENT)
            }
            break;    
        case TOKEN_PEOPLE_COUNT:
            console.log("People Count :"+message)
            peoplecount =Number(message)
            if(peoplecount > PEOPLE_THRESHOLD){
                people_count_ts=timestamp // reset timer
            }
            else if(minsPastSince(timestamp,people_count_ts) > ALONE_MINS_THRESHOLD){
                generateAlert(TOKEN_PEOPLE_COUNT)
            }
            break;

    }
}

function minsPastSince(inputts1,inputts2){
    var diff_mins=0;
    var date1_ms = inputts1.getTime();
    var date2_ms = inputts2.getTime();
    var diff_ms = date1_ms - date2_ms;
    diff_ms = diff_ms /1000;
    diff_mins = Math.floor(diff_ms / 60)
    return(diff_mins);
}

function generateAlert(type){
var alertMessage = " ";
    switch(type){
        case TOKEN_HEARTRATE:
            alertMessage = "Worker in distress : Heart rate low"
            break;
        case TOKEN_LOCATION_LAT:
        case TOKEN_LOCATION_LONG:
        case TOKEN_MOVEMENT :
            alertMessage = "Worker in distress : No Movement"
            break;
        case TOKEN_PEOPLE_COUNT:
            alertMessage = "Worker alone : More than "+ ALONE_MINS_THRESHOLD + " mins"
    }
      mqttClient.publish(TOKEN_ALERT, alertMessage)
}

function sendDweet(){
    dweetClient.dweet_for("LONE_WORKER",{ HEART_RATE: heartrate, PEOPLE_COUNT : peoplecount,LOCATION_LAT : prev_location_lat,
         LOCATION_LONG : prev_location_long, MOVEMENT : prev_movement },function(err, dweet){
            console.log("Dweet info from LONE_WORKER\n"+dweet); // "my-thing"
            console.log("ERR "+err); // "my-thing"
            })
}