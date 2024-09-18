#include "Ultrasound.hpp"
#include <iostream>
using namespace std;


//pass PIN NO through constructor
Ultrasound::Ultrasound(int pinNo)
{
        ID = pinNo;
}

//ISR for rising edge of ECHO pin
void isr_rise(void* args)
{
}

int Ultrasound::sensor_init()
{
        //memory allocation for ECHO and GPIO(trigger)
        gpio = new mraa::Gpio(ID);

     return EXIT_SUCCESS;                          
}                                                     
int Ultrasound::sensor_ISR_init()                     
{                                                     
        return EXIT_SUCCESS;                          
}                                                     
                                                      
float Ultrasound::sensor_read()                       
{                                                     
        diff = 0.0f;                                  
        gpio->useMmap(true);                          
                                                      
        response = (mraa_result_t)gpio->dir(mraa::DIR_OUT);
                                                           
                                                           
        response = (mraa_result_t)gpio->write(0);                       //Genera
        usleep(2);                                                              
        response = (mraa_result_t)gpio->write(1);                               
        usleep(10);                                                             
        response = (mraa_result_t)gpio->write(0);                               
                                                                                
        response = (mraa_result_t)gpio->dir(mraa::DIR_IN); 


  t2=clock();                                                             
                                                                                
        while (gpio->read() == 1)                                       //wait f
        {                                                                       
                t1=clock();                                                     
        }                                                                       
                                                                                
        diff=(((float)t1-(float)t2)/CLOCKS_PER_SEC) ;                           
                                                                                
        distance= (diff/2 * 34300);                                             
                                                                                
        if (distance<0)                                                         
                return 0;                                                       
        else                                                                    
                 return distance;                                               
                                                                                
}                                                                               
                                                                                
Ultrasound::~Ultrasound()                                                       
{                                                                               
        delete gpio;                                                            
        delete echo;


}
