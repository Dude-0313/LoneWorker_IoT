#include <iostream>
#include <unistd.h>
#include <math.h>
#include <mosquitto.h>
#include <upm/jhd1313m1.h> // LCD Module
#include "Ultrasound.hpp"
#include <upm/grove.h>
#include <mraa/aio.hpp>
#include <upm/buzzer.h>

/************************Hardware Related Macros************************************/
#define         MQ_PIN                       (0)     //define which analog input channel you are going to use
#define         RL_VALUE                     (5)     //define the load resistance on the board, in kilo ohms
#define         RO_CLEAN_AIR_FACTOR          (9.83)  //RO_CLEAR_AIR_FACTOR=(Sensor resistance in clean air)/RO,
                                                     //which is derived from the chart in datasheet
 
/***********************Software Related Macros************************************/
#define         CALIBARAION_SAMPLE_TIMES     (50)    //define how many samples you are going to take in the calibration phase
#define         CALIBRATION_SAMPLE_INTERVAL  (500)   //define the time interal(in milisecond) between each samples in the
                                                     //cablibration phase
#define         READ_SAMPLE_INTERVAL         (50)    //define how many samples you are going to take in normal operation
#define         READ_SAMPLE_TIMES            (5)     //define the time interal(in milisecond) between each samples in 
                                                     //normal operation
 
/**********************Application Related Macros**********************************/
#define         GAS_LPG                      (0)
#define         GAS_CO                       (1)
#define         GAS_SMOKE                    (2)
#define         GAS_THRESH                   (10) // 10 PPM

#define         TEMP_VALUE                   (3)
#define         TEMP_MAX                     (60)
#define         TEMP_MIN                     (10)
#define         TEMP_PIN                     (3) // A3
#define         BVALUE                       (4275)               // B value of the thermistor
#define         R0                           (100000)            // R0 = 100k

#define         ALERT_NONE                   (0)
#define         ALERT_GAS                    (1)
#define         ALERT_TEMP                   (2)

#define         BUZZER_PIN                  (6) // D6
#define         US_PIN                      (4) // D4
#define         PROX_THRESH                 (500)


#define         MQTT_BROKER_HOST        "192.168.1.101"
#define         MQTT_BROKER_PORT        1883
#define         MQTT_KEEPALIVE          60
#define         MQTT_TOPIC_ALERT        "loneworker/alert"
#define         MQTT_TOPIC_GAS_LPG          "loneworker/gas/lpg"
#define         MQTT_TOPIC_GAS_CO           "loneworker/gas/co"
#define         MQTT_TOPIC_GAS_SMOKE        "loneworker/gas/smoke"
#define         MQTT_TOPIC_TEMP         "loneworker/temp"

#define         SUCCESS                     true
#define         FAILURE                     false

#define delay(x)  usleep(x*1000)

/*****************************Globals***********************************************/
float           LPGCurve[3]  =  {2.3,0.21,-0.47};   //two points are taken from the curve. 
                                                    //with these two points, a line is formed which is "approximately equivalent"
                                                    //to the original curve. 
                                                    //data format:{ x, y, slope}; point1: (lg200, 0.21), point2: (lg10000, -0.59) 
float           COCurve[3]  =  {2.3,0.72,-0.34};    //two points are taken from the curve. 
                                                    //with these two points, a line is formed which is "approximately equivalent" 
                                                    //to the original curve.
                                                    //data format:{ x, y, slope}; point1: (lg200, 0.72), point2: (lg10000,  0.15) 
float           SmokeCurve[3] ={2.3,0.53,-0.44};    //two points are taken from the curve. 
                                                    //with these two points, a line is formed which is "approximately equivalent" 
                                                    //to the original curve.
                                                    //data format:{ x, y, slope}; point1: (lg200, 0.53), point2: (lg10000,  -0.22)                                                     
float           Ro           =  10;                 //Ro is initialized to 10 kilo ohms

float GAS_LPG_Reading = 0.0;
float GAS_CO_Reading = 0.0 ;
float GAS_SMOKE_Reading =0.0;

float TEMP_Reading=0;

//char ssid[] = "JioFi_1670";           
//char pass[] = "abc123";   
//int status = WL_IDLE_STATUS;
//IPAddress server(74,125,115,105);  // Backend Server IP
//WiFiClient client;


upm::Jhd1313m1* LCD;
Ultrasound US_Sensor(US_PIN);
mraa::Aio mq_pin(MQ_PIN);
upm::GroveTemp *TempSensor;
upm:: Buzzer* buzzer;

static struct mosquitto *mosq=NULL;


#if 0 // Manually setting WiFi
void setupWiFi()
{
  cout << "Attempting to connect to WPA network...");
  cout << ("SSID: ");
  cout << ln(ssid);

  status = WiFi.begin(ssid, pass);
  if ( status != WL_CONNECTED) { 
    Serial.println("Couldn't get a wifi connection");
    while(true);
  } 
  else {
    Serial.println("Connected to wifi");
    Serial.println("\nStarting connection...");
    // if you get a connection, report back via serial:
    if (client.connect(server, 80)) {
      Serial.println("connected");
    }
  }  
}
#endif

int  MQGetPercentage(float rs_ro_ratio, float *pcurve)
{
  return (pow(10,( ((log(rs_ro_ratio)-pcurve[1])/pcurve[2]) + pcurve[0])));
}

float MQResistanceCalculation(int raw_adc)
{
  return ( ((float)RL_VALUE*(1023-raw_adc)/raw_adc));
}
 
float MQRead()
{
  int i;
  float rs=0.0;
 
  for (i=0;i<READ_SAMPLE_TIMES;i++) {
    rs += MQResistanceCalculation(mq_pin.read());
    delay(READ_SAMPLE_INTERVAL);
  }
 
  rs = rs/READ_SAMPLE_TIMES;
 
  return rs;  
}
int MQGetGasPercentage(float rs_ro_ratio, int gas_id)
{
  if ( gas_id == GAS_LPG ) {
     return MQGetPercentage(rs_ro_ratio,LPGCurve);
  } else if ( gas_id == GAS_CO ) {
     return MQGetPercentage(rs_ro_ratio,COCurve);
  } else if ( gas_id == GAS_SMOKE ) {
     return MQGetPercentage(rs_ro_ratio,SmokeCurve);
  }    
 
  return 0;
}
 

float MQCalibration()
{
  int i;
  float val=0;
 
  for (i=0;i<CALIBARAION_SAMPLE_TIMES;i++) {            //take multiple samples
    val += MQResistanceCalculation(mq_pin.read());
    delay(CALIBRATION_SAMPLE_INTERVAL);
  }
  val = val/CALIBARAION_SAMPLE_TIMES;                   //calculate the average value
 
  val = val/RO_CLEAN_AIR_FACTOR;                        //divided by RO_CLEAN_AIR_FACTOR yields the Ro 
                                                        //according to the chart in the datasheet 
 
  return val; 
}



void mosq_log_callback(struct mosquitto *mosq, void *userdata, int level, const char *str)
{
	/* Pring all log messages regardless of level. */
  
  switch(level){
    //case MOSQ_LOG_DEBUG:
    //case MOSQ_LOG_INFO:
    //case MOSQ_LOG_NOTICE:
    case MOSQ_LOG_WARNING:
    case MOSQ_LOG_ERR: {
      printf("%i:%s\n", level, str);
    }
  }
  
	
}

void setupMQTT()
{
    cout << "init" <<endl;
    mosquitto_lib_init();
    cout << "MQ new" <<endl;
    mosq = mosquitto_new(NULL, true,NULL);
    if(!mosq) {
        cout << "ERROR : Out of memory" << endl;
        exit(1);
    }
    cout << "MQ callback" <<endl;

    mosquitto_log_callback_set(mosq, mosq_log_callback);

    cout << "MQ connect" <<endl;
  
    if(mosquitto_connect(mosq, MQTT_BROKER_HOST, MQTT_BROKER_PORT, 0)){
		cout << "ERROR: Unable to connect." << endl;
		exit(1);
	}
    int loop = mosquitto_loop_start(mosq);
    if(loop != MOSQ_ERR_SUCCESS){
  		cout << "ERROR: Unable to connect." << endl;
      exit(1);
    } 
    cout << "MQ End" <<endl;    
}

int mqtt_send(char* topic,char *msg){
  char buffer[20];
  time_t rawtime;
  struct tm * timeinfo;


  time (&rawtime);
  timeinfo = localtime (&rawtime);
  strftime (buffer,20,"%m/%d/%Y %H:%M:%S",timeinfo);
 // mosquitto_publish(mosq, NULL, MQTT_TOPIC_TIMESTAMP, strlen(buffer), buffer, 0, 0);
  return mosquitto_publish(mosq, NULL, topic, strlen(msg), msg, 0, 0);
}
void write2lcd(int line,const char *msg){
  LCD->setCursor(0,line);
  LCD->write(msg);   
}

void alertLCD(const char*msg) {
  LCD->clear();
  LCD->setColor(255,0,0);
  write2lcd(1,msg);
  //LCD->blinkLED();
}

void normalLCD() {
  LCD->clear();
  LCD->setColor(0,0,255);
  //LCD->noBlinkLED();
}


float getProximity() {
  float rangeInMeters=0.0;
  rangeInMeters = US_Sensor.sensor_read();
  cout << "Proximity :" << rangeInMeters << endl;
  return rangeInMeters;
}

void soundBuzzer(bool state) {
  if(state) {
    cout << "Sound Buzzer" <<endl;
     buzzer->playSound(DO,0);
  } else {
    buzzer->stopSound();
    cout << "UnSound Buzzer" << endl;
    
  }
} 

void setupLCD(){
    LCD->setCursor(16, 2);
    normalLCD();
}



void GetGasReadings()
{
   GAS_LPG_Reading = MQGetGasPercentage(MQRead()/Ro,GAS_LPG);
   GAS_CO_Reading = MQGetGasPercentage(MQRead()/Ro,GAS_CO);
   GAS_SMOKE_Reading = MQGetGasPercentage(MQRead()/Ro,GAS_SMOKE);
}

void GetTempReading(){
  //TEMP_Reading = TempSensor.read11(TEMP_PIN);
    TEMP_Reading = TempSensor->value()-15; // Temprature value in Celsius 
}
 
void ReadSensors(){
  GetGasReadings();
  GetTempReading();
}

void publishData(){
  char buf[20];
  sprintf(buf,"%f",GAS_LPG_Reading);
  mqtt_send(MQTT_TOPIC_GAS_LPG,buf);  
  cout << "Topic : " << MQTT_TOPIC_GAS_LPG << ":" << buf <<endl;
  sprintf(buf,"%f",GAS_CO_Reading);
  mqtt_send(MQTT_TOPIC_GAS_CO,buf);  
  sprintf(buf,"%f",GAS_SMOKE_Reading);
  mqtt_send(MQTT_TOPIC_GAS_SMOKE,buf);  
  sprintf(buf,"%f",TEMP_Reading);
  mqtt_send(MQTT_TOPIC_TEMP,buf);  
  
}


 



int Edge_Analytics()
{
  if((GAS_LPG_Reading > GAS_THRESH) ||  (GAS_CO_Reading > GAS_THRESH)|| (GAS_SMOKE_Reading > GAS_THRESH)) {
    return ALERT_GAS;
  }
  else if( (TEMP_Reading < TEMP_MIN) || (TEMP_Reading > TEMP_MAX)) {
    return ALERT_TEMP;  
  }
  return ALERT_NONE;
}

void soundAlert(char* alertMsg ) {
  mqtt_send(MQTT_TOPIC_ALERT,alertMsg);
  if(getProximity() < PROX_THRESH) {
    soundBuzzer(true);
  }
  else{
  soundBuzzer(false);
  }

}

void GenerateAlert(int type) {
  switch(type){
    case ALERT_NONE :   normalLCD();
                        soundBuzzer(false);
                      return;
    case ALERT_GAS : alertLCD("GAS Alert!!");
                      soundAlert("GAS Alert at Station 1!!");
                      break;
    case ALERT_TEMP : alertLCD("Temprature Alert!!");                      
                        soundAlert("Temprature Alert at Station 1!!");
                        break; 
  }
}

void setup()
{
  TempSensor=new upm::GroveTemp(TEMP_PIN);
  buzzer = new upm::Buzzer(BUZZER_PIN);
  LCD = new  upm::Jhd1313m1(0,0x3E,0x62);
  US_Sensor.sensor_init();
  soundBuzzer(false);
  setupLCD();
  //setupWiFi();
  cout << "Calibrating Gas Sensor..." << endl;                
  Ro = MQCalibration();                       //Calibrating the sensor. Please make sure the sensor is in clean air 
                                                    //when you perform the calibration                    
  cout << "Calibration complete ..." <<endl; 
  cout  << "Ro=" << Ro << "kohm" <<endl;
  setupMQTT();

}

int alert_type=ALERT_NONE;
bool isAlerting=false;

void loop()
{
/*  if(!mqttClient.connected())
  {
    setupMQTT();
  }
  else {
    delay(200);
    mqttClient.loop();   
  }
*/
  ReadSensors();
   if((alert_type=Edge_Analytics())!=ALERT_NONE){
      GenerateAlert(alert_type); 
      isAlerting=true;
   }
   else if(isAlerting)
   {
      GenerateAlert(ALERT_NONE);
      isAlerting=false;
   }
   cout << "LPG:" << GAS_LPG_Reading << "ppm" << "    " << "CO:" << GAS_CO_Reading << "ppm" << "    " << "SMOKE:" 
   << GAS_SMOKE_Reading << "ppm" << endl;   
   cout << "TEMP " << TEMP_Reading <<" C" <<endl;   
   publishData();
}


int main()
{
    setup();
    while(1){
      loop();
      delay(500);      
    }
}



