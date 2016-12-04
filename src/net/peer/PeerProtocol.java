/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.peer;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.Connections;
import util.FingerTable;
import util.HashFunction;
import util.Log;
import util.Node;
import util.NodeRef;
import util.file.FileRef;
import util.message.MessageType;
import util.message.MulticastMessage;
import util.message.UDPMessage;

/**
 *
 *
 */
class PeerProtocol {

    public static void check(Object msg){
        Connections con = Connections.instance;
        UDPMessage message;
        UDPMessage resp;
        MulticastMessage mm;
        PeerConnection peer;
        NodeRef routeToPeer;
        FileRef fRef;
        
        if(msg instanceof UDPMessage){
            message = (UDPMessage) msg;
            peer = con.getPeerConnectionList().get(message.getInitiator().getID());
            
            switch(message.getMessage()){
                
                case FINDSUCCESSOR://received a FINDSUCCESSOR MESSAGE;
                    // <editor-fold defaultstate="collapsed" desc="FINDSUCCESSOR">
                    routeToPeer = peer.getFingerTable().getNearestPeer(peer.getNode(), message.getSender().getID());
                    
                    if(routeToPeer.getID() == peer.getID()){
                        resp = new UDPMessage(MessageType.TELLSUCCESSOR,(Node)peer,peer.getSuccessor());
                        peer.getOutgoing().send(resp, message.getSender());
                        
                        peer.setPredecessor(message.getSender());
                        peer.setSuccessor(message.getSender());
                        
                        tranferCustodyOfFileRefs(peer, message.getSender());
                        
                        Node.printStatus(peer, false);
                        peer.getFingerTable().update(peer.getID(), message.getSender());
                    }
                    else{
                        peer.getFingerTable().update(peer.getID(), message.getSender());
                        peer.getOutgoing().send(message, routeToPeer);
                    }
                    // </editor-fold>
                    break;//end FINDSUCCESSOR MESSAGE
                case TELLSUCCESSOR://received a TELLSUCCESSOR MESSAGE
                    peer.setPredecessor(message.getSender());
                    NodeRef successor = (NodeRef)message.getAttachment();
                    peer.setSuccessor(successor);
                    
                    resp = new UDPMessage(MessageType.TELLPREDECESSOR,(Node)peer);
                    peer.getOutgoing().send(resp, peer.getSuccessor());
                    
                    Node.printStatus(peer, false);
                    peer.getFingerTable().update(peer.getID(), peer.getPredecessor());
                    if(!peer.getPredecessor().equals(peer.getSuccessor()))
                        peer.getFingerTable().update(peer.getID(), peer.getSuccessor());
                    break;//end TELLSUCCESSOR MESSAGE
                case TELLPREDECESSOR://received a TELLPREDECESSOR MESSAGE
                    peer.setPredecessor(message.getSender());
                    if(!peer.getPredecessor().equals(peer.getPreviousPredecessor()))
                        Node.printStatus(peer, false);
                    peer.getFingerTable().update(peer.getID(), peer.getPredecessor());
                    break;//end TELLPREDECESSOR MESSAGE
                case PUBLISH://received a PUBLISH MESSAGE
                    // <editor-fold defaultstate="collapsed" desc="PUBLISH">
                    fRef = (FileRef)message.getAttachment();
                    
                    routeToPeer = peer.getFingerTable().getNearestPeer(peer.getNode(), fRef.getID());
                    
                    if(routeToPeer.getID() == peer.getID()){
                        peer.addToRefencedFiles(fRef);
                        if(fRef.getPublisher().getID() == peer.getID()){
                            mm = new MulticastMessage(MessageType.PUBLISH,peer.getNode(),fRef);
                            Connections.multicastConnection.getOutgoing().send(mm);
                        }
                        else{
                            resp = new UDPMessage(MessageType.TELLPUBLISHER,(Node)peer, fRef);
                            peer.getOutgoing().send(resp, fRef.getPublisher());
                        }
                    }
                    else{
                        peer.getOutgoing().send(message, routeToPeer);//route the message
                    }

                    // </editor-fold>
                    break;//end PUBLISH MESSAGE
                case TELLPUBLISHER://received a TELLPUBLISHER MESSAGE
                    fRef = (FileRef)message.getAttachment();
                    Log.print("------------------------------------------------------------");
                    Log.print("The file you published for the P2P Network: "+HashFunction.displayID(peer.getInitiator().getID()) +"@"+
                        peer.getInitiator().getPort());
                    Log.print("Has been successfully REGISTERED to: " +HashFunction.displayID(message.getNode().getID()) +"@"+ 
                        message.getNode().getPort());
                    Log.print("FileID: "+HashFunction.displayID(fRef.getID()));
                    Log.print("FileName: "+fRef.getFileName());
                    Log.print("------------------------------------------------------------");
                    mm = new MulticastMessage(MessageType.PUBLISH,message.getSender(),fRef);
                    Connections.multicastConnection.getOutgoing().send(mm);
                    break;//end TELLPUBLISHED MESSAGE
                case DELETE://received DELETE MESSAGE
                    // <editor-fold defaultstate="collapsed" desc="DELETE">
                    fRef = (FileRef) message.getAttachment();
                    
                    routeToPeer = peer.getFingerTable().getNearestPeer(peer.getNode(), fRef.getID());
                    
                    if(routeToPeer.getID() == peer.getID()){
                        if(peer.deleteFileRef(fRef)!=null){//if fileReference exists
                            if(fRef.getPublisher().getID()==peer.getID()){//if the peer who requests to delete the file is the publisher "itself"
                                peer.deleteNetFile(fRef, peer.getNode());
                                mm = new MulticastMessage(MessageType.DELETE,peer.getNode(),fRef);
                                Connections.multicastConnection.getOutgoing().send(mm);
                            }
                            else{//send the delete request to the publisher
                               resp = new UDPMessage(MessageType.DELETEFILEPUBLISHER, message.getNode(), message.getAttachment());
                               peer.getOutgoing().send(resp, fRef.getPublisher());
                            }
                        }
                        else{//fileRefence not found! delete unsuccessful
                            if(message.getSender().getID()==peer.getID()){
                                Log.printErr("Failed to DELETE "+fRef.getFileName());
                                Log.printErr("File not found!");
                            }
                            else{//send DELETEFAIL to the peer who issued the delete request
                                resp = new UDPMessage(MessageType.DELETEFAIL, (Node)peer, message.getAttachment());
                                peer.getOutgoing().send(resp, message.getSender());
                            }
                        }
                    }
                    else{
                        peer.getOutgoing().send(message, routeToPeer);//route the message
                    }

                    // </editor-fold>
                    break;//end DELETE MESSAGE
                case DELETEFILEPUBLISHER:
                    fRef = (FileRef) message.getAttachment();
                    peer.deleteNetFile(fRef, message.getSender());
                    mm = new MulticastMessage(MessageType.DELETE,message.getSender(),fRef);
                    Connections.multicastConnection.getOutgoing().send(mm);
                    break;
                case DELETEFAIL:
                    fRef = (FileRef) message.getAttachment();
                    Log.printErr("Failed to DELETE "+fRef.getFileName());
                    Log.printErr("File not found!");
                    break;
                case RETRIEVE:
                    // <editor-fold defaultstate="collapsed" desc="RETRIEVE">
                    fRef = (FileRef) message.getAttachment();
                    
                    routeToPeer = peer.getFingerTable().getNearestPeer(peer.getNode(), fRef.getID());
                    
                    if(routeToPeer.getID() == peer.getID()){
                        if(peer.isReferenced(fRef)){//fileReference exist
                            if(fRef.getPublisher().getID() == message.getSender().getID()){//if the sender is also the publisher
                                if(peer.getID() == message.getSender().getID()){//if the sender is YOU
                                    con.addLocalFile(peer.getPublishedFiles().get(fRef.getID()));
                                }
                                else{
                                    resp = new UDPMessage(MessageType.INIT_COPY, (Node)peer, fRef);
                                    peer.getOutgoing().send(resp, fRef.getPublisher());
                                }
                            }
                            else{
                                resp = new UDPMessage(MessageType.INIT_SEND, message.getNode(), fRef);
                                peer.getOutgoing().send(resp, fRef.getPublisher());
                            }
                        }
                        else{//file not found!
                            if(message.getSender().getID()==peer.getID()){//
                                Log.printErr("Failed to RETRIEVE "+fRef.getFileName());
                                Log.printErr("File not found!");
                            }
                            else{
                                resp = new UDPMessage(MessageType.FAILRETRIEVE, (Node)peer, fRef);
                                peer.getOutgoing().send(resp, message.getSender());
                            }
                        }
                    }
                    else{
                        peer.getOutgoing().send(message, routeToPeer);//route the message
                    }
                    
                    // </editor-fold>
                    break;//end RETRIEVE
                case INIT_COPY://the requestor is the publisher
                    fRef = (FileRef) message.getAttachment();
                    con.addLocalFile(peer.getPublishedFiles().get(fRef.getID()));
                    break;
                case INIT_SEND:
                    NodeRef sender = message.getSender();
                    fRef = (FileRef) message.getAttachment();
                    peer.openFileObjSender(sender,fRef);
                    break;
                case INIT_RECEIVE:
                    peer.openFileObjReceiver((NodeRef)message.getAttachment());
                    break;
                case FAILRETRIEVE:
                    fRef = (FileRef) message.getAttachment();
                    Log.printErr("Failed to RETRIEVE "+fRef.getFileName());
                    Log.printErr("File not found!");
                    break;
                case TRANS_REF:
                    Map<Long, FileRef> fRefs = (Map<Long, FileRef>) message.getAttachment();
                    fRefs.forEach((f_id,f_ref) -> peer.addToRefencedFiles(f_ref));
                    break;
            }
                    
        }
        else{
            Log.print("Received an unknown message");
        }
    }


    private static Predicate<? super Map.Entry<Long, FileRef>> isImmediateSuccessor(long senderID, long peerPrevSuccessorID) {
        return f -> FingerTable.isSuccessor(f.getKey(),senderID, peerPrevSuccessorID);
    }
    
    private static void tranferCustodyOfFileRefs(PeerConnection peer, NodeRef sender) {
        
        Map<Long, FileRef> refsToBeTransfered = peer.getReferencedFiles().entrySet()
            .stream()
            .filter(isImmediateSuccessor(sender.getID(),peer.getPreviousSuccessor().getID()))//filters the fileRefs to be transferred to the joining Node
            .collect(Collectors.toMap(f -> f.getKey(), f -> f.getValue()));
        
        refsToBeTransfered.forEach((id,fRef) -> peer.deleteFileRef(fRef));//delete the fileRefs keep by the current peer

        UDPMessage m = new UDPMessage(MessageType.TRANS_REF, (Node)peer, refsToBeTransfered);
        peer.getOutgoing().send(m, sender);//send the TRANS_REF message to the joining Node

    }

    
}
