# ifndef ULTRASOUND_H
# define ULTRASOUND_H

#include "mraa.hpp"

using namespace std;

class Ultrasound
{
        private:
                mraa::Gpio* gpio ;
                mraa_result_t response;
                float diff;
                float distance;
                clock_t t1,t2;
                int ID;
                volatile float isr_args;

       public:                         
                                        
                mraa::Gpio* echo;       
                Ultrasound(int pinNo);  
                int sensor_init();      
                int sensor_ISR_init();  
                float sensor_read();    
                ~Ultrasound();          
};                                      
                                        
                                        
# endif   
