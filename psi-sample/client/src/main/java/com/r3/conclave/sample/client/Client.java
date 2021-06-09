package com.r3.conclave.sample.client;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.r3.conclave.client.InvalidEnclaveException;
import com.r3.conclave.common.EnclaveInstanceInfo;
import com.r3.conclave.mail.Curve25519PrivateKey;
import com.r3.conclave.mail.EnclaveMail;
import com.r3.conclave.mail.PostOffice;
import com.r3.conclave.sample.common.AdDetails;
import com.r3.conclave.sample.common.InputData;
import com.r3.conclave.sample.common.InputDataSerializer;
import com.r3.conclave.sample.common.UserDetails;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client can be of two types- Merchant or Service Provider
 * Merchant - who supplies list of users who have made a purchase
 * Service Providers - who supplies a list of users who have clicked on the ad
 * Both the clients send the lists to enclave, which calculates the ad conversion rate and sends it back to the clients
 */
public class Client {

    public static void main(String args[]) throws InterruptedException, IOException, InvalidEnclaveException {

        //STEP 1: Connect to Host Server
        DataInputStream fromHost;
        DataOutputStream toHost;
        while (true) {
            try {
                System.out.println("Attempting to connect to localhost:9999");
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), 9999), 5000);
                fromHost = new DataInputStream(socket.getInputStream());
                toHost = new DataOutputStream(socket.getOutputStream());
                break;
            } catch (Exception e) {
                System.err.println("Retrying: " + e.getMessage());
                Thread.sleep(2000);
            }
        }

        //STEP 2: Take inputs from user
        InputData inputData = new InputData();
        List<UserDetails> userDetailsList = null;
        List<AdDetails> adDetailsList= null;
        if (args.length == 0) {
            System.err.println("Please pass [MERCHANT/SERVICE-PROVIDER] followed by list of credit card numbers separated by spaces");
            return;
        }

        String type = args[0];

        if("MERCHANT".equals(type)) {
            userDetailsList = new ArrayList<>(args.length);
            for (int i =2;i< args.length ; i++) {
                UserDetails userDetails = new UserDetails(args[i]);
                userDetailsList.add(userDetails);
            }
            inputData.setUserDetailsList(userDetailsList);

        } else if("SERVICE-PROVIDER".equals(type)) {
            adDetailsList = new ArrayList<>(args.length);
            for (int i =2;i< args.length ; i++) {
                AdDetails adDetails = new AdDetails(args[i]);
                adDetailsList.add(adDetails);
            }
            inputData.setAdDetailsList(adDetailsList);
        }

        //STEP 3: Retrieve the attestation object from Host immediately after connecting to Host
        byte[] attestationBytes = new byte[fromHost.readInt()];
        fromHost.readFully(attestationBytes);

        //STEP 4: Convert byte[] received from host to EnclaveInstanceInfo object
        EnclaveInstanceInfo instanceInfo = EnclaveInstanceInfo.deserialize(attestationBytes);

        //STEP 5: Create a dummy key pair for sending via mail to enclave
        PrivateKey key = Curve25519PrivateKey.random();

        //STEP 5: Create PostOffice specifying - clients public key, topic name , enclaves public key
        PostOffice postOffice = instanceInfo.createPostOffice(key, UUID.randomUUID().toString());

        //STEP 6: Encrypt the message using enclave's public key
        byte[] encryptedRequest = postOffice.encryptMail(serializeMessage(inputData).getBuffer(), type.getBytes());

        //STEP 7: Send the encrypted Mail to To Host to relay it to enclave
        toHost.writeInt(encryptedRequest.length);
        toHost.write(encryptedRequest);

        //STEP 8: Get the reply back from host via the socket
        byte[] encryptedReply = new byte[fromHost.readInt()];
        fromHost.readFully(encryptedReply);

        //STEP 8: Use Post Office to decrypt back the mail sent by the enclave
        EnclaveMail mail = postOffice.decryptMail(encryptedReply);

        System.out.println("Ad Conversion Rate : " + new String(mail.getBodyAsBytes()) +"%");

        toHost.close();
        fromHost.close();
    }

    /**
     * Use Kryo to serialize inputs from client to enclave
     */
    private static Output serializeMessage(InputData listOfCreditCardNumbers){
        Kryo kryo = new Kryo();
        Output output = new Output(new ByteArrayOutputStream());
        kryo.register(InputData.class, new InputDataSerializer());
        kryo.writeObject(output, listOfCreditCardNumbers);
        output.close();
        return output;
    }
}
