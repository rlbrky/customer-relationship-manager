package com.berkay.crm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig {

    // THIS CHANGES JSON SHAPE:
    /*
    *
    * // before (PageImpl)              // after (PagedModel)
    {                                 {
      "content": [...],                 "content": [...],
      "totalElements": 5,               "page": {
      "totalPages": 1,                    "size": 20,
      "number": 0,                        "number": 0,
      "size": 20                          "totalElements": 5,
    }                                     "totalPages": 1
                                        }
                                      }
    *
    * */
}
