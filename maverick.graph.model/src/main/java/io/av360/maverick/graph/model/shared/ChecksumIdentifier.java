package io.av360.maverick.graph.model.shared;

import com.google.common.hash.Hashing;
import io.av360.maverick.graph.model.errors.store.InvalidEntityModel;
import io.av360.maverick.graph.model.rdf.LocalIRI;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;

/**
 * The generated entity identifier needs to resolve, it should be in the form
 * <p>
 * http://example.org/api/entities/{id}
 * http://example.org/api/transactions/{id}
 * <p>
 * FIXME: should require the current id (either bnode or externally set) to create reproducible ids
 * FIXME: should also keep track of the original id (we should store this in the provenance)
 */
@Slf4j
public class ChecksumIdentifier extends LocalIRI implements LocalIdentifier  {
    private static final char[][] range = {{'a', 'z'}, {'0', '9'}};

    private static final Checksum checksum = new CRC32C();




    public ChecksumIdentifier(String namespace, Resource oldIdentifier) throws InvalidEntityModel {
        super(namespace);

        if (oldIdentifier instanceof IRI iri) {
            // TODO: generate fingerprint of old resource with fixed length
            super.setLocalName(generateIdentifier(iri));
        } else {
            log.warn("Generating identifier for anonymous node, falling back to random identifier");
            throw new InvalidEntityModel("Generating checksum identifier of resource (not IRI): "+ oldIdentifier);
        }
    }



    public ChecksumIdentifier(String namespace, IRI oldIdentifier) {
        super(namespace);
        super.setLocalName(generateIdentifier(oldIdentifier));
    }


    /**
     * Generates a new and reproducible identifier from the old resource identifier (its namespace) and a characteristic property
     * @param namespace the new local namespace
     * @param values characteristic property (rdfs:label, dc:identifier, ...)
     */
    public ChecksumIdentifier(Namespace namespace, Value ... values) {

        super(namespace.getName());

        String collect = Arrays.stream(values).map(Value::stringValue).collect(Collectors.joining());
        super.setLocalName(generateChecksum(collect));
    }



    private String generateIdentifier(IRI iri) {
        return this.generateIdentifier(iri.getNamespace(), iri.getLocalName());
    }

    private String generateIdentifier(String first, String ... others) {
        StringBuilder stringBuilder = new StringBuilder(first);
        Arrays.stream(others).forEach(stringBuilder::append);

        return this.generateFingerprint(stringBuilder.toString());
    }






    private String generateChecksum(String val) {
        checksum.reset();
        checksum.update(val.getBytes(), 0, val.length());

        return this.dec2Base(BigInteger.valueOf(checksum.getValue()));

    }

    private  String generateFingerprint(String val) {
        long l = Hashing.murmur3_32_fixed().hashString(val, StandardCharsets.UTF_8).padToLong();
        return this.dec2Base(BigInteger.valueOf(l));
    }

    //private final char[] alphabet = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private final char[] alphabet = "abcdefghijklmnopqrstuvwyz0123456789_".toCharArray();

    private String dec2Base(BigInteger number) {
        Stack<Integer> stack = new Stack<>();

        do {
            BigInteger[] divisionResultAndReminder = number.divideAndRemainder( BigInteger.valueOf(alphabet.length) );
            stack.push(divisionResultAndReminder[1].intValue());
            number = divisionResultAndReminder[0];
        } while(!number.equals(BigInteger.ZERO));

        StringBuilder result = new StringBuilder();
        while(! stack.empty()) {
            result.append(alphabet[stack.pop()]);
        }
        String ser = result.toString();
        if(ser.length() > LENGTH) return ser.substring(0, LENGTH-1);
        if(ser.length() < LENGTH) return normalize(ser);
        else return ser;


    }

    private String normalize(String str) {
        if (str.length() >= LENGTH) {
            return str;
        }

        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < LENGTH) {
            sb.append(PADDING_CHAR);
        }

        return sb.toString();

    }


}
