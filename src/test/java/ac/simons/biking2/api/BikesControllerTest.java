/*
 * Copyright 2014 Michael J. Simons.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ac.simons.biking2.api;

import ac.simons.biking2.persistence.entities.Bike;
import ac.simons.biking2.persistence.repositories.BikeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static java.time.LocalDate.now;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.stub;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Michael J. Simons, 2014-02-20
 */
public class BikesControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void shouldGetBikes() {
	final BikeRepository repository = mock(BikeRepository.class);
	stub(repository.findAll(any(Sort.class))).toReturn(new ArrayList<>());
	stub(repository.findByDecommissionedOnIsNull(any(Sort.class))).toReturn(new ArrayList<>());
	
	final BikesController controller = new BikesController(repository);
		
	List<Bike> bikes = controller.getBikes(false);
	Assert.assertNotNull(bikes);
	Assert.assertEquals(0, bikes.size());
	
	bikes = controller.getBikes(true);
	Assert.assertNotNull(bikes);
	Assert.assertEquals(0, bikes.size());
	
	Mockito.verify(repository).findAll(Mockito.any(Sort.class));
	Mockito.verify(repository).findByDecommissionedOnIsNull(Mockito.any(Sort.class));
	Mockito.verifyNoMoreInteractions(repository);	
    }
    
    @Test
    public void testCreateMilage() throws Exception {
	LocalDate now = now();

	final BikeRepository repository = mock(BikeRepository.class);

	final Bike bike = new Bike("testBike", now);
	stub(repository.findOne(2)).toReturn(bike);
	final Bike decommissionedBike = new Bike("decommissioned", now.minusMonths(2).withDayOfMonth(1));	
	decommissionedBike.decommission(now.minusMonths(1));
	stub(repository.findOne(3)).toReturn(decommissionedBike);

	final NewMilageCmd newMilageCmd = new NewMilageCmd();
	newMilageCmd.setAmount(23.0);
	newMilageCmd.setRecordedOn(new Date());

	final BikesController controller = new BikesController(repository);
	final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

	// Empty content
	mockMvc
		.perform(post("http://biking.michael-simons.eu/api/bikes/1/milages").contentType(APPLICATION_JSON))
		.andExpect(status().isBadRequest())
		.andExpect(MockMvcResultMatchers.content().string(""));

	// Invalid content
	mockMvc
		.perform(
			post("http://biking.michael-simons.eu/api/bikes/1/milages")
			.contentType(APPLICATION_JSON)
			.content("{}")
		)
		.andExpect(status().isBadRequest())
		.andExpect(MockMvcResultMatchers.content().string("Invalid arguments."));

	// Invalid bike
	mockMvc
		.perform(
			post("http://biking.michael-simons.eu/api/bikes/1/milages")
			.contentType(APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(newMilageCmd))
		)
		.andExpect(status().isNotFound())
		.andExpect(MockMvcResultMatchers.content().string(""));

	// Valid request
	mockMvc
		.perform(
			post("http://biking.michael-simons.eu/api/bikes/2/milages")
			.contentType(APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(newMilageCmd))
		)
		.andExpect(status().isOk())
		.andExpect(content().string(
				objectMapper.writeValueAsString(new Bike("testBike", now).addMilage(now, 23.0)))
		);
	
	// Decommisioned bike
	mockMvc
		.perform(
			post("http://biking.michael-simons.eu/api/bikes/3/milages")
			.contentType(APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(newMilageCmd))
		)
		.andExpect(status().isBadRequest())
		.andExpect(MockMvcResultMatchers.content().string("Bike has already been decommissioned."));
	
	verify(repository, times(1)).findOne(1);
	verify(repository, times(1)).findOne(2);
	verify(repository, times(1)).findOne(3);
	verify(repository, times(1)).save(any(Bike.class));
    }

    @Test
    public void testCreateBike1() throws Exception {
	LocalDate now = now();

	final BikeRepository repository = mock(BikeRepository.class);
	when(repository.save(any(Bike.class))).then(returnsFirstArg());	
	
	final BikesController controller = new BikesController(repository);
	final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

	final BikeCmd newBikeCmd = new BikeCmd();
	newBikeCmd.setBoughtOn(new Date());
	newBikeCmd.setColor("cccccc");
	newBikeCmd.setName("test");

	final Bike bike = new Bike("test", now);
	bike.setColor("cccccc");

	// Empty content
	mockMvc
		.perform(post("http://biking.michael-simons.eu/api/bikes").contentType(APPLICATION_JSON))
		.andExpect(status().isBadRequest())
		.andExpect(MockMvcResultMatchers.content().string(""));

	// Invalid content
	mockMvc
		.perform(
			post("http://biking.michael-simons.eu/api/bikes")
			.contentType(APPLICATION_JSON)
			.content("{}")
		)
		.andExpect(status().isBadRequest())
		.andExpect(MockMvcResultMatchers.content().string("Invalid arguments."));

	// Valid request
	mockMvc
		.perform(
			post("http://biking.michael-simons.eu/api/bikes")
			.contentType(APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(newBikeCmd))
		)
		.andExpect(status().isOk())
		.andExpect(content().string(
				objectMapper.writeValueAsString(bike))
		);

	verify(repository, times(1)).save(any(Bike.class));
    }

    @Test
    public void testCreateBike2() throws Exception {
	LocalDate now = now();

	final BikeRepository repository = mock(BikeRepository.class);
	stub(repository.save(any(Bike.class))).toThrow(new DataIntegrityViolationException(""));

	final BikesController controller = new BikesController(repository);
	final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

	final BikeCmd newBikeCmd = new BikeCmd();
	newBikeCmd.setBoughtOn(new Date());
	newBikeCmd.setColor("cccccc");
	newBikeCmd.setName("test");

	mockMvc
		.perform(
			post("http://biking.michael-simons.eu/api/bikes")
			.contentType(APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(newBikeCmd))
		)
		.andExpect(status().isConflict())
		.andExpect(MockMvcResultMatchers.content().string(""));

	verify(repository, times(1)).save(any(Bike.class));
    }
    
    @Test
    public void testUpdateBike() throws Exception {
	LocalDate now = now();

	final BikeRepository repository = mock(BikeRepository.class);
	final Bike decommissionedBike = new Bike("decommissioned", now.minusMonths(2).withDayOfMonth(1));	
	decommissionedBike.decommission(now.minusMonths(1));
	stub(repository.findOne(3)).toReturn(decommissionedBike);
	
	Bike bike = new Bike("test", now.minusMonths(1));
	bike.setColor("000000");
	Calendar boughtOn = bike.getBoughtOn();
	stub(repository.findOne(2)).toReturn(bike);
	
	final BikesController controller = new BikesController(repository);
	final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

	final BikeCmd updatedBikeCmd = new BikeCmd();
	updatedBikeCmd.setBoughtOn(new Date());
	updatedBikeCmd.setDecommissionedOn(new Date());	
	updatedBikeCmd.setColor("FFFCCC");
	updatedBikeCmd.setName("neuer name");	
	
	// Empty content
	mockMvc
		.perform(put("http://biking.michael-simons.eu/api/bikes/1").contentType(APPLICATION_JSON))
		.andExpect(status().isBadRequest())
		.andExpect(MockMvcResultMatchers.content().string(""));

	// Invalid content
	mockMvc
		.perform(
			put("http://biking.michael-simons.eu/api/bikes/1")
			.contentType(APPLICATION_JSON)
			.content("{}")
		)
		.andExpect(status().isBadRequest())
		.andExpect(MockMvcResultMatchers.content().string("Invalid arguments."));
	
	// Invalid content
	mockMvc
		.perform(
			put("http://biking.michael-simons.eu/api/bikes/1")
			.contentType(APPLICATION_JSON)
			.content("{}")
		)
		.andExpect(status().isBadRequest())
		.andExpect(MockMvcResultMatchers.content().string("Invalid arguments."));

	// Valid request, invalid bike
	mockMvc
		.perform(
			put("http://biking.michael-simons.eu/api/bikes/1")
			.contentType(APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(updatedBikeCmd))
		)
		.andExpect(status().isNotFound())
		.andExpect(MockMvcResultMatchers.content().string(""));
	
	mockMvc
		.perform(
			put("http://biking.michael-simons.eu/api/bikes/2")
			.contentType(APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(updatedBikeCmd))
		)
		.andExpect(status().isOk());
	
	// Decommisioned bike
	mockMvc
		.perform(
			put("http://biking.michael-simons.eu/api/bikes/3")
			.contentType(APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(updatedBikeCmd))
		)
		.andExpect(status().isBadRequest())
		.andExpect(MockMvcResultMatchers.content().string("Bike has already been decommissioned."));

	verify(repository, times(1)).findOne(1);
	verify(repository, times(1)).findOne(2);
	verify(repository, times(1)).findOne(3);

	Assert.assertEquals("test", bike.getName());
	Assert.assertEquals(boughtOn, bike.getBoughtOn());
	Assert.assertEquals("FFFCCC", bike.getColor());
	Assert.assertEquals(GregorianCalendar.from(now.atStartOfDay(ZoneId.systemDefault())), bike.getDecommissionedOn());

	verifyNoMoreInteractions(repository);
    }
}
