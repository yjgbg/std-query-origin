package com.github.yjgbg.demo.ctl;

import com.github.yjgbg.demo.jackson.GraphedEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestCtl {
	@RequestMapping("nohidden")
	public Person test() {
		final var p0 = new Person();
		final var a0 = new Address();
		p0.address = a0;
		p0.name = "p0";
		a0.city = "city";
		a0.country = "country";
		a0.person = p0;
		return p0;
	}

	@Getter
	@Setter
	public static class Person implements GraphedEntity {
		private String name;
		private Address address;
	}

	@Getter
	@Setter
	public static class Address implements GraphedEntity {
		private String city;
		private String country;
		private Person person;
	}
}
