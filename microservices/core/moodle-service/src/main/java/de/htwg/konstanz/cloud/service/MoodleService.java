package de.htwg.konstanz.cloud.service;

import de.htwg.konstanz.cloud.models.MoodleCredentials;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;


// to create a RESTful Controller (add Controller and ResponseBody)
@RestController
public class MoodleService {


    @RequestMapping(value = "/update", method = RequestMethod.POST)
    public ResponseEntity<?> getCourses(@RequestBody MoodleCredentials input) {


        // make a request to moodle in order to obtain the token
        RestTemplate req = new RestTemplate();
        //req.postForObject("moodle", )


        return new ResponseEntity<Object>("", HttpStatus.OK);

    }

}
