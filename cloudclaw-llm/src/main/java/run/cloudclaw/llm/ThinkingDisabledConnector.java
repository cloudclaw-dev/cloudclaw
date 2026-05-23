package run.cloudclaw.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

/**
 * ClientHttpConnector that patches request bodies to inject
 * "thinking": {"type": "disabled"} for DeepSeek API requests.
 *
 * Intercepts writeWith() to collect the full body, patch the JSON,
 * then forward the modified body to the delegate.
 */
public class ThinkingDisabledConnector implements ClientHttpConnector {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    private final ClientHttpConnector delegate = new ReactorClientHttpConnector();

    @Override
    public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri,
            Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {
        return delegate.connect(method, uri,
                request -> requestCallback.apply(new PatchingRequest(request)));
    }

    private class PatchingRequest implements ClientHttpRequest {
        private final ClientHttpRequest delegate;

        PatchingRequest(ClientHttpRequest delegate) {
            this.delegate = delegate;
        }

        @Override public HttpMethod getMethod() { return delegate.getMethod(); }
        @Override public URI getURI() { return delegate.getURI(); }
        @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
        @Override public MultiValueMap<String, HttpCookie> getCookies() { return delegate.getCookies(); }
        @Override public Map<String, Object> getAttributes() { return delegate.getAttributes(); }
        @Override public DefaultDataBufferFactory bufferFactory() { return new DefaultDataBufferFactory(); }
        @Override public <T> T getNativeRequest() { return delegate.getNativeRequest(); }
        @Override public Mono<Void> setComplete() { return delegate.setComplete(); }
        @Override public boolean isCommitted() { return delegate.isCommitted(); }
        @Override public void beforeCommit(java.util.function.Supplier<? extends Mono<Void>> action) { delegate.beforeCommit(action); }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return DataBufferUtils.join(Flux.from(body))
                    .flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        bytes = patchThinking(bytes);
                        return delegate.writeWith(Mono.just(bufferFactory.wrap(bytes)));
                    });
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            // Treat same as writeWith - join all chunks, patch, send as one
            return Flux.from(body)
                    .concatMap(Flux::from)
                    .collectList()
                    .flatMap(buffers -> {
                        return DataBufferUtils.join(Flux.fromIterable(buffers))
                                .flatMap(dataBuffer -> {
                                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                    dataBuffer.read(bytes);
                                    DataBufferUtils.release(dataBuffer);
                                    bytes = patchThinking(bytes);
                                    return delegate.writeWith(Mono.just(bufferFactory.wrap(bytes)));
                                });
                    });
        }

        private byte[] patchThinking(byte[] bytes) {
            try {
                String bodyStr = new String(bytes, StandardCharsets.UTF_8);
                if (!bodyStr.contains("\"thinking\"")) {
                    ObjectNode node = (ObjectNode) objectMapper.readTree(bodyStr);
                    ObjectNode thinking = objectMapper.createObjectNode();
                    thinking.put("type", "disabled");
                    node.set("thinking", thinking);
                    return objectMapper.writeValueAsBytes(node);
                }
            } catch (Exception e) {
                // If patching fails, return original bytes
            }
            return bytes;
        }
    }
}
