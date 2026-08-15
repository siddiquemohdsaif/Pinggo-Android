package com.w3n.pinggo.Database.Firestore.Util;

public class Validator {

    public static void validateDocumentName(String documentName) {

        if (documentName == null){
            throw new RuntimeException("document name is null");
        }

        if (documentName.equals("")){
            throw new RuntimeException("document name is empty");
        }

        if (documentName.contains("`")){
            throw new RuntimeException("document name haven a restricted character (`)");
        }

        if (documentName.contains("/")){
            throw new RuntimeException("document name have a restricted character (/)");
        }

        if (documentName.contains(" ")){
            throw new RuntimeException("document name have a restricted character (space)");
        }
    }


    public static void validateCollectionName(String collectionName) {

        if (collectionName == null){
            throw new RuntimeException("collection name is null");
        }

        if (collectionName.equals("")){
            throw new RuntimeException("collection name is empty");
        }

        if (collectionName.contains("`")){
            throw new RuntimeException("collection name have a restricted character (`)");
        }

        if (collectionName.contains("/")){
            throw new RuntimeException("collection name have a restricted character (/)");
        }

        if (collectionName.contains(" ")){
            throw new RuntimeException("collection name have a restricted character (space)");
        }
    }

}
