package com.github.yjgbg.demo.cfg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yjgbg.demo.jackson.MappingGraphedOutputEntity2HttpMessageConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

import javax.servlet.http.HttpServletRequest;

@RequiredArgsConstructor
@SpringBootConfiguration
public class ConverterConfig {
	private final HttpServletRequest request;
	private final ObjectMapper objectMapper;

	@Bean
	public MappingGraphedOutputEntity2HttpMessageConverter graphedOutputEntity2HttpMessageConverter() {
		return new MappingGraphedOutputEntity2HttpMessageConverter(
				() -> request.getParameterValues("includes"),
				() -> request.getParameterValues("excludes"),
				objectMapper
		);
	}
}
