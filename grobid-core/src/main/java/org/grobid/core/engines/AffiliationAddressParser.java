package org.grobid.core.engines;

import org.chasen.crfpp.Tagger;
import org.grobid.core.GrobidModels;
import org.grobid.core.data.Affiliation;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.features.FeaturesVectorAffiliationAddress;
import org.grobid.core.lexicon.Lexicon;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.utilities.TextUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Patrice Lopez
 */
public class AffiliationAddressParser extends AbstractParser {
    public Lexicon lexicon = Lexicon.getInstance();

    public AffiliationAddressParser() {
        super(GrobidModels.AFFIILIATON_ADDRESS);
    }

    public ArrayList<Affiliation> processing(String input) {
        try {
            ArrayList<String> affiliationBlocks = new ArrayList<String>();
            input = input.trim();

            input = TextUtilities.dehyphenize(input);
            StringTokenizer st = new StringTokenizer(input, " \n\t" + TextUtilities.fullPunctuations, true);

            ArrayList<String> tokenizations = new ArrayList<String>();
            while (st.hasMoreTokens()) {
                String tok = st.nextToken();
                if (tok.length() == 0) continue;
                if (tok.equals("\n")) {
                    tokenizations.add(" ");
                } else {
                    tokenizations.add(tok);
                }
                if (!tok.equals(" ")) {
                    if (tok.equals("\n")) {
                        affiliationBlocks.add("@newline");
                    }
                    affiliationBlocks.add(tok + " <affiliation>");
                }
            }


            List<List<OffsetPosition>> placesPositions = new ArrayList<List<OffsetPosition>>();
            placesPositions.add(lexicon.inCityNames(input));

            String header = FeaturesVectorAffiliationAddress.addFeaturesAffiliationAddress(affiliationBlocks, placesPositions);

            // add context
//            st = new StringTokenizer(header, "\n");

            //TODO:
            String res = label(header);
            return resultBuilder(res, tokenizations, false); // don't use pre-labels
        } catch (Exception e) {
            throw new GrobidException("An exception occurred while running Grobid.", e);
        }
    }

    /**
     * Post processing of extracted field affiliation and address.
     * Here the input string to be processed comes from a previous parser: the segmentation
     * can be kept and we filter in all tokens labelled <address> or <affiliation>.
     * We also need to keep the original tokenization information to recreate the exact
     * initial string.
     */
    public ArrayList<Affiliation> processReflow(String result,
                                                ArrayList<String> tokenizations) throws Exception {
        try {
            ArrayList<String> affiliationBlocks = new ArrayList<String>();
            ArrayList<String> subTokenizations = new ArrayList<String>();

            filterAffiliationAddress(result, tokenizations, affiliationBlocks, subTokenizations);

            //System.out.println(affiliationBlocks.toString());
            //System.out.println(subTokenizations.toString());

            return processingReflow(affiliationBlocks, subTokenizations);
        } catch (Exception e) {
//			e.printStackTrace();
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
//		return null;
    }


    private void filterAffiliationAddress(String result,
                                          ArrayList<String> tokenizations,
                                          ArrayList<String> affiliationBlocks,
                                          ArrayList<String> subTokenizations) {
        StringTokenizer st = new StringTokenizer(result, "\n");
        //System.out.println(result);
        //System.out.println(tokenizations.toString());
        String lastLabel = null;
        int lineCount = 0;
        int p = 0;
        while (st.hasMoreTokens() && (p < tokenizations.size())) {
            String toke = tokenizations.get(p);
            List<String> tokes = new ArrayList<String>();
            while ((toke.equals(" ") || toke.equals("\n") || (toke.length() == 0)) && ((p+1) < tokenizations.size())) {
                p++;
                if (toke.length() == 0) {
                    toke = tokenizations.get(p);
                    continue;
                }
                tokes.add(toke);
                if (toke.equals("\n")) {
                    affiliationBlocks.add("@newline");
                }
                toke = tokenizations.get(p);
            }

            String line = st.nextToken();
            if (line.length() == 0) {
                continue;
            }

            if (line.trim().length() == 0) {
                affiliationBlocks.add("\n");
                //subTokenizations.add(" ");
                lastLabel = null;
            } else {
                StringTokenizer st2 = new StringTokenizer(line, "\t ");
                String label = null;
                String token = null;

                if (st2.hasMoreTokens()) {
                    // token is the first "feature"
                    token = st2.nextToken().trim();
                }

                while (st2.hasMoreTokens()) {
                    // label is the last "feature"
                    label = st2.nextToken();
                }

                if ((label.indexOf("affiliation") != -1) || (label.indexOf("address") != -1)) {
                    affiliationBlocks.add(token + " " + label);
                    for (String tok : tokes) {
                        subTokenizations.add(tok);
                    }
                    subTokenizations.add(toke);
                } else if (lastLabel != null) {
                    affiliationBlocks.add("\n");
                }

                if ((label.indexOf("affiliation") != -1) || (label.indexOf("address") != -1)) {
                    lastLabel = label;
                } else {
                    lastLabel = null;
                }
            }
            lineCount++;
            p++;
        }
    }

    private ArrayList<Affiliation> processingReflow(ArrayList<String> affiliationBlocks,
                                                    ArrayList<String> tokenizations) throws Exception {
        try {
            String res = runReflow(affiliationBlocks, tokenizations);
            return resultBuilder(res, tokenizations, false); // normally use pre-label because it is a reflow
        } catch (Exception e) {
//			e.printStackTrace();
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
    }

    private String runReflow(ArrayList<String> affiliationBlocks,
                             ArrayList<String> tokenizations) {
        StringBuilder res = new StringBuilder();
        Tagger tagger = null;
        try {
            List<List<OffsetPosition>> placesPositions = new ArrayList<List<OffsetPosition>>();
            placesPositions.add(lexicon.inCityNames(tokenizations));
            String header =
                    FeaturesVectorAffiliationAddress.addFeaturesAffiliationAddress(affiliationBlocks, placesPositions);

            // clear internal context
            int n = 0;
            StringTokenizer st = new StringTokenizer(header, "\n");

            //TODO: VZ: understand how tagging is done and how we can utilize wapiti
            tagger = null;//getNewTagger();

            ArrayList<String> preToken = new ArrayList<String>();

            // add context
            while (st.hasMoreTokens()) {
                String piece = st.nextToken();
                if (piece.trim().length() != 0) {
                    tagger.add(piece);
                    tagger.add("\n");
                    String pretok = piece.substring(piece.lastIndexOf(' '), piece.length());
                    preToken.add(pretok);
                } else {
                    tagger.add("\n");

                    // parse and change internal stated as 'parsed'
                    if (!tagger.parse()) {
                        // throw an exception
                        throw new Exception("CRF++ parsing failed.");
                    }

                    for (int i = 0; i < tagger.size(); i++) {
                        for (int j = 0; j < tagger.xsize(); j++) {
                            //System.out.print(tagger.x(i, j) + "\t");
                            res.append(tagger.x(i, j) + "\t");
                        }

                        res.append(preToken.get(i) + "\t");
                        res.append(tagger.y2(i));
                        res.append("\n");
                    }
                    res.append(" \n");
                    tagger.clear();
                }
                n++;
            }

            // parse and change internal stated as 'parsed'
            if (!tagger.parse()) {
                // throw an exception
                throw new Exception("CRF++ parsing failed.");
            }

            for (int i = 0; i < tagger.size(); i++) {
                for (int j = 0; j < tagger.xsize(); j++) {
                    //System.out.print(tagger.x(i, j) + "\t");
                    res.append(tagger.x(i, j) + "\t");
                }

                res.append(preToken.get(i) + "\t");
                res.append(tagger.y2(i));
                res.append("\n");
            }
            res.append(" \n");

            return res.toString();
        } catch (Exception e) {
            throw new GrobidException("An exception occured while running Grobid.", e);
        } finally {
            if (tagger != null) {
                tagger.delete();
            }
        }
    }


    private ArrayList<Affiliation> resultBuilder(String result,
                                                 ArrayList<String> tokenizations,
                                                 boolean usePreLabel) {
        ArrayList<Affiliation> fullAffiliations = null;
        try {
            //System.out.println(tokenizations.toString());
            // extract results from the processed file
            StringTokenizer st2 = new StringTokenizer(result, "\n");
            String lastTag = null;
            org.grobid.core.data.Affiliation aff = new Affiliation();
            int lineCount = 0;
            boolean hasInstitution;
            boolean hasDepartment = false;
            boolean hasAddress = false;
            boolean hasLaboratory;
            boolean newMarker = false;
            boolean useMarker = false;
            String currentMarker = null;

            int p = 0;

            while (st2.hasMoreTokens()) {
                boolean addSpace = false;
                String line = st2.nextToken();
                Integer lineCountInt = lineCount;
                if (line.trim().length() == 0) {
                    if (aff.notNull()) {
                        if (fullAffiliations == null) {
                            fullAffiliations = new ArrayList<Affiliation>();
                        }
                        fullAffiliations.add(aff);
                        aff = new Affiliation();
                        currentMarker = null;
                    }
                    hasInstitution = false;
                    hasDepartment = false;
                    hasLaboratory = false;
                    hasAddress = false;
                    continue;
                }
                StringTokenizer st3 = new StringTokenizer(line, "\t");
                int ll = st3.countTokens();
                int i = 0;
                String s1 = null;
                String s2 = null;
                String s3 = null;
                ArrayList<String> localFeatures = new ArrayList<String>();
                while (st3.hasMoreTokens()) {
                    String s = st3.nextToken().trim();
                    if (i == 0) {
                        s2 = s; // lexical token

                        boolean strop = false;
                        while ((!strop) && (p < tokenizations.size())) {
                            String tokOriginal = tokenizations.get(p);
                            if (tokOriginal.equals(" ")) {
                                addSpace = true;
                            } else if (tokOriginal.equals(s)) {
                                strop = true;
                            }
                            p++;
                        }
                    } else if (i == ll - 2) {
                        s3 = s; // pre-label
                    } else if (i == ll - 1) {
                        s1 = s; // label
                    } else {
                        localFeatures.add(s);
                    }
                    i++;
                }

                if (s1.equals("<marker>")) {
                    if (currentMarker == null)
                        currentMarker = s2;
                    else {
                        if (addSpace) {
                            currentMarker += " " + s2;
                        } else
                            currentMarker += s2;
                    }
                    aff.setMarker(currentMarker);
                    newMarker = false;
                    useMarker = true;
                } else if (s1.equals("I-<marker>")) {
                    currentMarker = s2;
                    newMarker = true;
                    useMarker = true;
                }

                if (newMarker) {
                    if (aff.notNull()) {
                        if (fullAffiliations == null)
                            fullAffiliations = new ArrayList<Affiliation>();
                        fullAffiliations.add(aff);
                    }

                    aff = new Affiliation();
                    hasInstitution = false;
                    hasLaboratory = false;
                    hasDepartment = false;
                    hasAddress = false;

                    if (currentMarker != null) {
                        aff.setMarker(currentMarker);
                    }
                    newMarker = false;
                } else if (s1.equals("<institution>") || s1.equals("I-<institution>")) {
                    if ((!usePreLabel) ||
                            ((usePreLabel) && (s3.equals("<affiliation>") || s3.equals("I-<affiliation>")))
                            ) {
                        hasInstitution = true;
                        if (aff.getInstitutions() != null) {
                            if (s1.equals("I-<institution>") &&
                                    (localFeatures.contains("LINESTART"))) {
                                // new affiliation
                                if (aff.notNull()) {
                                    if (fullAffiliations == null)
                                        fullAffiliations = new ArrayList<Affiliation>();
                                    fullAffiliations.add(aff);
                                }
                                hasInstitution = true;
                                hasDepartment = false;
                                hasLaboratory = false;
                                hasAddress = false;
                                aff = new Affiliation();
                                aff.addInstitution(s2);
                                if (currentMarker != null)
                                    aff.setMarker(currentMarker.trim());
                            } else if (s1.equals("I-<institution>") && hasInstitution && hasAddress &&
                                    (!lastTag.equals("<institution>"))) {
                                // new affiliation
                                if (aff.notNull()) {
                                    if (fullAffiliations == null) {
                                        fullAffiliations = new ArrayList<Affiliation>();
                                    }
                                    fullAffiliations.add(aff);
                                }
                                hasInstitution = true;
                                hasDepartment = false;
                                hasLaboratory = false;
                                hasAddress = false;
                                aff = new Affiliation();
                                aff.addInstitution(s2);
                                if (currentMarker != null) {
                                    aff.setMarker(currentMarker.trim());
                                }
                            } else if (s1.equals("I-<institution>")) {
                                // we have multiple institutions for this affiliation
                                //aff.addInstitution(aff.institution);
                                aff.addInstitution(s2);
                            } else if (addSpace) {
                                aff.extendLastInstitution(" " + s2);
                            } else {
                                aff.extendLastInstitution(s2);
                            }
                        } else {
                            aff.addInstitution(s2);
                        }
                    } else if ((usePreLabel) && (s3.equals("<address>") || s3.equals("I-<address>"))) {
                        // that's a piece of the address badly labelled according to the model
                        if (aff.getAddressString() != null) {
                            if (addSpace) {
                                aff.setAddressString(aff.getAddressString() + " " + s2);
                            } else {
                                aff.setAddressString(aff.getAddressString() + s2);
                            }
                        } else {
                            aff.setAddressString(s2);
                        }
                    }
                } else if (s1.equals("<addrLine>") || s1.equals("I-<addrLine>")) {
                    if ((!usePreLabel) ||
                            ((usePreLabel) && ((s3.equals("<address>") || s3.equals("I-<address>"))))) {
                        if (aff.getAddrLine() != null) {
                            if (s1.equals(lastTag) || lastTag.equals("I-<addrLine>")) {
                                if (s1.equals("I-<addrLine>")) {
                                    aff.setAddrLine(aff.getAddrLine() + " ; " + s2);
                                } else if (addSpace) {
                                    aff.setAddrLine(aff.getAddrLine() + " " + s2);
                                } else {
                                    aff.setAddrLine(aff.getAddrLine() + s2);
                                }
                            } else {
                                aff.setAddrLine(aff.getAddrLine() + ", " + s2);
                            }
                        } else {
                            aff.setAddrLine(s2);
                        }
                        hasAddress = true;
                    } else if ((usePreLabel) && ((s3.equals("<affiliation>") || s3.equals("I-<affiliation>")))) {
                        if (aff.getAffiliationString() != null) {
                            if (s1.equals(lastTag)) {
                                if (addSpace) {
                                    aff.setAffiliationString(aff.getAffiliationString() + " " + s2);
                                } else {
                                    aff.setAffiliationString(aff.getAffiliationString() + s2);
                                }
                            } else {
                                aff.setAffiliationString(aff.getAffiliationString() + " ; " + s2);
                            }
                        } else {
                            aff.setAffiliationString(s2);
                        }
                    }
                } else if (s1.equals("<department>") || s1.equals("I-<department>")) {
                    if ((!usePreLabel) ||
                            ((usePreLabel) && (s3.equals("<affiliation>") || s3.equals("I-<affiliation>")))
                            ) {
                        if (aff.getDepartments() != null) {
                            /*if (localFeatures.contains("LINESTART"))
                                       aff.department += " " + s2;*/

                            if ((s1.equals("I-<department>")) &&
                                    (localFeatures.contains("LINESTART"))
                                    ) {
                                if (aff.notNull()) {
                                    if (fullAffiliations == null)
                                        fullAffiliations = new ArrayList<Affiliation>();
                                    fullAffiliations.add(aff);
                                }
                                hasInstitution = false;
                                hasDepartment = true;
                                hasLaboratory = false;
                                hasAddress = false;
                                aff = new Affiliation();
                                aff.addDepartment(s2);
                                if (currentMarker != null) {
                                    aff.setMarker(currentMarker.trim());
                                }
                            } else if ((s1.equals("I-<department>")) && hasDepartment && hasAddress &&
                                    !lastTag.equals("<department>")) {
                                if (aff.notNull()) {
                                    if (fullAffiliations == null) {
                                        fullAffiliations = new ArrayList<Affiliation>();
                                    }
                                    fullAffiliations.add(aff);
                                }
                                hasInstitution = false;
                                hasDepartment = true;
                                hasAddress = false;
                                hasLaboratory = false;
                                aff = new Affiliation();
                                aff.addDepartment(s2);
                                if (currentMarker != null) {
                                    aff.setMarker(currentMarker.trim());
                                }
                            } else if (s1.equals("I-<department>")) {
                                // we have multiple departments for this affiliation
                                aff.addDepartment(s2);
                                //aff.department = s2;
                            } else if (addSpace) {
                                //aff.extendFirstDepartment(" " + s2);
                                aff.extendLastDepartment(" " + s2);
                            } else {
                                //aff.extendFirstDepartment(s2);
                                aff.extendLastDepartment(s2);
                            }
                        } else if (aff.getInstitutions() != null) {
                            /*if (localFeatures.contains("LINESTART"))
                                       aff.department += " " + s2;*/

                            if ((s1.equals("I-<department>")) && hasAddress &&
                                    (localFeatures.contains("LINESTART"))
                                    ) {
                                if (aff.notNull()) {
                                    if (fullAffiliations == null)
                                        fullAffiliations = new ArrayList<Affiliation>();
                                    fullAffiliations.add(aff);
                                }
                                hasInstitution = false;
                                hasDepartment = true;
                                hasLaboratory = false;
                                hasAddress = false;
                                aff = new Affiliation();
                                aff.addDepartment(s2);
                                if (currentMarker != null) {
                                    aff.setMarker(currentMarker.trim());
                                }
                            } else {
                                aff.addDepartment(s2);
                            }
                        } else {
                            aff.addDepartment(s2);
                        }
                    } else if ((usePreLabel) && (s3.equals("<address>") || s3.equals("I-<address>"))) {
                        if (aff.getAddressString() != null) {
                            if (addSpace) {
                                aff.setAddressString(aff.getAddressString() + " " + s2);
                            } else {
                                aff.setAddressString(aff.getAddressString() + s2);
                            }
                        } else {
                            aff.setAddressString(s2);
                        }
                    }
                } else if (s1.equals("<laboratory>") || s1.equals("I-<laboratory>")) {
                    if ((!usePreLabel) ||
                            ((usePreLabel) && (s3.equals("<affiliation>") || s3.equals("I-<affiliation>")))
                            ) {
                        hasLaboratory = true;
                        if (aff.getLaboratories() != null) {
                            if (s1.equals("I-<laboratory>") &&
                                    (localFeatures.contains("LINESTART"))) {
                                // new affiliation
                                if (aff.notNull()) {
                                    if (fullAffiliations == null)
                                        fullAffiliations = new ArrayList<Affiliation>();
                                    fullAffiliations.add(aff);
                                }
                                hasInstitution = false;
                                hasLaboratory = true;
                                hasDepartment = false;
                                hasAddress = false;
                                aff = new Affiliation();
                                aff.addLaboratory(s2);
                                if (currentMarker != null) {
                                    aff.setMarker(currentMarker.trim());
                                }
                            } else if (s1.equals("I-<laboratory>")
                                    && hasLaboratory
                                    && hasAddress
                                    && (!lastTag.equals("<laboratory>"))) {
                                // new affiliation
                                if (aff.notNull()) {
                                    if (fullAffiliations == null)
                                        fullAffiliations = new ArrayList<Affiliation>();
                                    fullAffiliations.add(aff);
                                }
                                hasInstitution = false;
                                hasLaboratory = true;
                                hasDepartment = false;
                                hasAddress = false;
                                aff = new Affiliation();
                                aff.addLaboratory(s2);
                                if (currentMarker != null) {
                                    aff.setMarker(currentMarker.trim());
                                }
                            } else if (s1.equals("I-<laboratory>")) {
                                // we have multiple laboratories for this affiliation
                                aff.addLaboratory(s2);
                            } else if (addSpace) {
                                aff.extendLastLaboratory(" " + s2);
                            } else {
                                aff.extendLastLaboratory(s2);
                            }
                        } else {
                            aff.addLaboratory(s2);
                        }
                    } else if ((usePreLabel) && (s3.equals("<address>") || s3.equals("I-<address>"))) {
                        // that's a piece of the address badly labelled
                        if (aff.getAddressString() != null) {
                            if (addSpace) {
                                aff.setAddressString(aff.getAddressString() + " " + s2);
                            } else {
                                aff.setAddressString(aff.getAddressString() + s2);
                            }
                        } else {
                            aff.setAddressString(s2);
                        }
                    }
                } else if (s1.equals("<country>") || s1.equals("I-<country>")) {
                    if ((!usePreLabel) ||
                            ((usePreLabel) && ((s3.equals("<address>") || s3.equals("I-<address>"))))) {
                        if (aff.getCountry() != null) {
                            if (s1.equals("I-<country>")) {
                                aff.setCountry(aff.getCountry() + ", " + s2);
                            } else if (addSpace) {
                                aff.setCountry(aff.getCountry() + " " + s2);
                            } else {
                                aff.setCountry(aff.getCountry() + s2);
                            }
                        } else {
                            aff.setCountry(s2);
                        }
                        hasAddress = true;
                    } else if ((usePreLabel) && ((s3.equals("<affiliation>") || s3.equals("I-<affiliation>")))) {
                        if (aff.getAffiliationString() != null) {
                            if (addSpace) {
                                aff.setAffiliationString(aff.getAffiliationString() + " " + s2);
                            } else {
                                aff.setAffiliationString(aff.getAffiliationString() + s2);
                            }
                        } else {
                            aff.setAffiliationString(s2);
                        }
                    }
                } else if (s1.equals("<postCode>") || s1.equals("I-<postCode>")) {
                    if ((!usePreLabel) ||
                            ((usePreLabel) && ((s3.equals("<address>") || s3.equals("I-<address>"))))) {
                        if (aff.getPostCode() != null) {
                            if (s1.equals("I-<postCode>")) {
                                aff.setPostCode(aff.getPostCode() + ", " + s2);
                            } else if (addSpace) {
                                aff.setPostCode(aff.getPostCode() + " " + s2);
                            } else {
                                aff.setPostCode(aff.getPostCode() + s2);
                            }
                        } else {
                            aff.setPostCode(s2);
                        }
                    } else if ((usePreLabel) && ((s3.equals("<affiliation>") || s3.equals("I-<affiliation>")))) {
                        if (aff.getAffiliationString() != null) {
                            if (addSpace) {
                                aff.setAffiliationString(aff.getAffiliationString() + " " + s2);
                            } else {
                                aff.setAffiliationString(aff.getAffiliationString() + s2);
                            }
                        } else {
                            aff.setAffiliationString(s2);
                        }
                    }
                } else if (s1.equals("<postBox>") || s1.equals("I-<postBox>")) {
                    if ((!usePreLabel) ||
                            ((usePreLabel) && ((s3.equals("<address>") || s3.equals("I-<address>"))))) {
                        if (aff.getPostBox() != null) {
                            if (s1.equals("I-<postBox>")) {
                                aff.setPostBox(aff.getPostBox() + ", " + s2);
                            } else if (addSpace) {
                                aff.setPostBox(aff.getPostBox() + " " + s2);
                            } else {
                                aff.setPostBox(aff.getPostBox() + s2);
                            }
                        } else {
                            aff.setPostBox(s2);
                        }
                    } else if ((usePreLabel) && ((s3.equals("<affiliation>") || s3.equals("I-<affiliation>")))) {
                        if (aff.getAffiliationString() != null) {
                            if (addSpace) {
                                aff.setAffiliationString(aff.getAffiliationString() + " " + s2);
                            } else {
                                aff.setAffiliationString(aff.getAffiliationString() + s2);
                            }
                        } else {
                            aff.setAffiliationString(s2);
                        }
                    }
                } else if (s1.equals("<region>") || s1.equals("I-<region>")) {
                    if ((!usePreLabel) ||
                            ((usePreLabel) && ((s3.equals("<address>") || s3.equals("I-<address>"))))) {
                        if (aff.getRegion() != null) {
                            if (s1.equals("I-<region>")) {
                                aff.setRegion(aff.getRegion() + ", " + s2);
                            } else if (addSpace) {
                                aff.setRegion(aff.getRegion() + " " + s2);
                            } else {
                                aff.setRegion(aff.getRegion() + s2);
                            }
                        } else {
                            aff.setRegion(s2);
                        }
                    } else if ((usePreLabel) && ((s3.equals("<affiliation>") || s3.equals("I-<affiliation>")))) {
                        if (aff.getAffiliationString() != null) {
                            if (addSpace) {
                                aff.setAffiliationString(aff.getAffiliationString() + " " + s2);
                            } else {
                                aff.setAffiliationString(aff.getAffiliationString() + s2);
                            }
                        } else {
                            aff.setAffiliationString(s2);
                        }
                    }
                } else if (s1.equals("<settlement>") || s1.equals("I-<settlement>")) {
                    if ((!usePreLabel) ||
                            ((usePreLabel) && ((s3.equals("<address>") || s3.equals("I-<address>"))))) {
                        if (aff.getSettlement() != null) {
                            if (s1.equals("I-<settlement>")) {
                                aff.setSettlement(aff.getSettlement() + ", " + s2);
                            } else if (addSpace) {
                                aff.setSettlement(aff.getSettlement() + " " + s2);
                            } else {
                                aff.setSettlement(aff.getSettlement() + s2);
                            }
                        } else {
                            aff.setSettlement(s2);
                        }
                        hasAddress = true;
                    } else if ((usePreLabel) && ((s3.equals("<affiliation>") || s3.equals("I-<affiliation>")))) {
                        if (aff.getAffiliationString() != null) {
                            if (addSpace) {
                                aff.setAffiliationString(aff.getAffiliationString() + " " + s2);
                            } else {
                                aff.setAffiliationString(aff.getAffiliationString() + s2);
                            }
                        } else {
                            aff.setAffiliationString(s2);
                        }
                    }
                }

                lastTag = s1;
                lineCount++;
                newMarker = false;
            }
            if (aff.notNull()) {
                if (fullAffiliations == null)
                    fullAffiliations = new ArrayList<Affiliation>();

                fullAffiliations.add(aff);
                hasInstitution = false;
                hasDepartment = false;
                hasAddress = false;
            }

            // we clean a little bit
            if (fullAffiliations != null) {
                for (Affiliation affi : fullAffiliations) {
                    affi.clean();
                }
            }
        } catch (Exception e) {
//			e.printStackTrace();
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
        return fullAffiliations;
    }

    /**
     * Extract results from a labelled header in the training format without any string modification.
     */
    public StringBuffer trainingExtraction(String result,
                                           ArrayList<String> tokenizations) {
        ArrayList<String> affiliationBlocks = new ArrayList<String>();
        ArrayList<String> tokenizationsAffiliation = new ArrayList<String>();

        filterAffiliationAddress(result, tokenizations, affiliationBlocks, tokenizationsAffiliation);
        String resultAffiliation = runReflow(affiliationBlocks, tokenizationsAffiliation);

        StringTokenizer st = new StringTokenizer(resultAffiliation, "\n");
        String s1 = null;
        String s2 = null;
        String lastTag = null;

        int p = 0;

        StringBuffer bufferAffiliation = new StringBuffer();

        String currentTag0 = null;
        String lastTag0 = null;
        boolean hasAddressTag = false;
        boolean hasAffiliationTag = false;
        boolean hasAddress = false;
        boolean hasAffiliation = false;
        boolean start = true;
        boolean tagClosed = false;
        while (st.hasMoreTokens()) {
            boolean addSpace = false;
            String tok = st.nextToken().trim();

            if (tok.length() == 0) {
                continue;
            }
            StringTokenizer stt = new StringTokenizer(tok, "\t");
            ArrayList<String> localFeatures = new ArrayList<String>();
            int i = 0;

            boolean newLine = false;
            int ll = stt.countTokens();
            while (stt.hasMoreTokens()) {
                String s = stt.nextToken().trim();
                if (i == 0) {
                    s2 = TextUtilities.HTMLEncode(s);

                    boolean strop = false;
                    while ((!strop) && (p < tokenizationsAffiliation.size())) {
                        String tokOriginal = tokenizationsAffiliation.get(p);
                        if (tokOriginal.equals(" ")) {
                            addSpace = true;
                        } else if (tokOriginal.equals(s)) {
                            strop = true;
                        }
                        p++;
                    }
                } else if (i == ll - 1) {
                    s1 = s;
                } else {
                    localFeatures.add(s);
                    if (s.equals("LINESTART") && !start) {
                        newLine = true;
                        start = false;
                    } else if (s.equals("LINESTART")) {
                        start = false;
                    }
                }
                i++;
            }


            lastTag0 = null;
            if (lastTag != null) {
                if (lastTag.startsWith("I-")) {
                    lastTag0 = lastTag.substring(2, lastTag.length());
                } else {
                    lastTag0 = lastTag;
                }
            }
            currentTag0 = null;
            if (s1 != null) {
                if (s1.startsWith("I-")) {
                    currentTag0 = s1.substring(2, s1.length());
                } else {
                    currentTag0 = s1;
                }
            }

            if (lastTag != null) {
                tagClosed = testClosingTag(bufferAffiliation, currentTag0, lastTag0);
            } else
                tagClosed = false;

            if (newLine) {
                if (tagClosed) {
                    bufferAffiliation.append("\t\t\t\t\t\t\t<lb/>\n");
                } else {
                    bufferAffiliation.append("<lb/>");
                }

            }

            String output = writeField(s1, lastTag0, s2, "<marker>", "<marker>", addSpace, 7);
            if (output != null) {
                if (hasAddressTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t\t</address>\n");
                    hasAddressTag = false;
                }
                if (hasAffiliationTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t</affiliation>\n");
                    hasAffiliationTag = false;
                }
                bufferAffiliation.append("\t\t\t\t\t\t<affiliation>\n" + output);
                hasAffiliationTag = true;
                hasAddressTag = false;
                hasAddress = false;
                hasAffiliation = false;
                lastTag = s1;
                continue;
            } else {
                output = writeField(s1, lastTag0, s2, "<institution>", "<orgName type=\"institution\">", addSpace, 7);
            }
            if (output == null) {
                output = writeField(s1, lastTag0, s2, "<department>", "<orgName type=\"department\">", addSpace, 7);
            } else {
                if (hasAddressTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t\t</address>\n");
                    hasAddressTag = false;
                }
                if (hasAddress && hasAffiliation) {
                    bufferAffiliation.append("\t\t\t\t\t\t</affiliation>\n");
                    hasAffiliationTag = false;
                    hasAddress = false;
                    hasAffiliation = false;
                    hasAddressTag = false;
                }

                if (!hasAffiliationTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t<affiliation>\n");
                    hasAffiliationTag = true;
                }
                bufferAffiliation.append(output);
                hasAffiliation = true;
                lastTag = s1;
                continue;
            }
            if (output == null) {
                output = writeField(s1, lastTag0, s2, "<laboratory>", "<orgName type=\"laboratory\">", addSpace, 7);
            } else {
                if (hasAddressTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t\t</address>\n");
                    hasAddressTag = false;
                }
                if (hasAddress && hasAffiliation) {
                    bufferAffiliation.append("\t\t\t\t\t\t</affiliation>\n");
                    hasAffiliationTag = false;
                    hasAddress = false;
                    hasAffiliation = false;
                    hasAddressTag = false;
                }

                if (!hasAffiliationTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t<affiliation>\n");
                    hasAffiliationTag = true;
                }
                bufferAffiliation.append(output);
                lastTag = s1;
                hasAffiliation = true;
                continue;
            }
            if (output == null) {
                output = writeField(s1, lastTag0, s2, "<addrLine>", "<addrLine>", addSpace, 8);
            } else {
                if (hasAddressTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t\t</address>\n");
                    hasAddressTag = false;
                }
                if (hasAddress && hasAffiliation) {
                    bufferAffiliation.append("\t\t\t\t\t\t</affiliation>\n");
                    hasAffiliationTag = false;
                    hasAddress = false;
                    hasAffiliation = false;
                    hasAddressTag = false;
                }

                if (!hasAffiliationTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t<affiliation>\n");
                    hasAffiliationTag = true;
                }
                bufferAffiliation.append(output);
                hasAffiliation = true;
                lastTag = s1;
                continue;
            }
            if (output == null) {
                output = writeField(s1, lastTag0, s2, "<postCode>", "<postCode>", addSpace, 8);
            } else {
                if (hasAddress && hasAffiliation) {
                    bufferAffiliation.append("\t\t\t\t\t\t</affiliation>\n");
                    hasAffiliationTag = false;
                    hasAddress = false;
                    hasAffiliation = false;
                    hasAddressTag = false;
                }
                if (!hasAffiliationTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t<affiliation>\n");
                    hasAffiliationTag = true;
                    hasAddressTag = false;
                }

                if (!hasAddressTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t\t<address>\n");
                    hasAddressTag = true;
                }
                bufferAffiliation.append(output);
                lastTag = s1;
                continue;
            }
            if (output == null) {
                output = writeField(s1, lastTag0, s2, "<postBox>", "<postBox>", addSpace, 8);
            } else {
                if (hasAddress && hasAffiliation) {
                    bufferAffiliation.append("\t\t\t\t\t\t</affiliation>\n");
                    hasAffiliationTag = false;
                    hasAddress = false;
                    hasAffiliation = false;
                    hasAddressTag = false;
                }
                if (!hasAffiliationTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t<affiliation>\n");
                    hasAffiliationTag = true;
                    hasAddressTag = false;
                }

                if (!hasAddressTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t\t<address>\n");
                    hasAddressTag = true;
                }
                bufferAffiliation.append(output);
                lastTag = s1;
                continue;
            }
            if (output == null) {
                output = writeField(s1, lastTag0, s2, "<region>", "<region>", addSpace, 8);
            } else {
                if (hasAddress && hasAffiliation) {
                    bufferAffiliation.append("\t\t\t\t\t\t</affiliation>\n");
                    hasAffiliationTag = false;
                    hasAddress = false;
                    hasAffiliation = false;
                    hasAddressTag = false;
                }
                if (!hasAffiliationTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t<affiliation>\n");
                    hasAffiliationTag = true;
                    hasAddressTag = false;
                }

                if (!hasAddressTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t\t<address>\n");
                    hasAddressTag = true;
                }
                bufferAffiliation.append(output);
                lastTag = s1;
                continue;
            }
            if (output == null) {
                output = writeField(s1, lastTag0, s2, "<settlement>", "<settlement>", addSpace, 8);
            } else {
                if (hasAddress && hasAffiliation) {
                    bufferAffiliation.append("\t\t\t\t\t\t</affiliation>\n");
                    hasAffiliationTag = false;
                    hasAddress = false;
                    hasAffiliation = false;
                    hasAddressTag = false;
                }
                if (!hasAffiliationTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t<affiliation>\n");
                    hasAffiliationTag = true;
                    hasAddressTag = false;
                }

                if (!hasAddressTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t\t<address>\n");
                    hasAddressTag = true;
                }
                bufferAffiliation.append(output);
                lastTag = s1;
                continue;
            }
            if (output == null) {
                output = writeField(s1, lastTag0, s2, "<country>", "<country>", addSpace, 8);
            } else {
                if (hasAddress && hasAffiliation) {
                    bufferAffiliation.append("\t\t\t\t\t\t</affiliation>\n");
                    hasAffiliationTag = false;
                    hasAddress = false;
                    hasAffiliation = false;
                    hasAddressTag = false;
                }
                if (!hasAffiliationTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t<affiliation>\n");
                    hasAffiliationTag = true;
                    hasAddressTag = false;
                }

                if (!hasAddressTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t\t<address>\n");
                    hasAddressTag = true;
                }
                bufferAffiliation.append(output);
                lastTag = s1;
                continue;
            }
            if (output == null) {
                output = writeField(s1, lastTag0, s2, "<other>", "<other>", addSpace, 8);
            } else {
                if (hasAddress && hasAffiliation) {
                    bufferAffiliation.append("\t\t\t\t\t\t</affiliation>\n");
                    hasAffiliationTag = false;
                    hasAddress = false;
                    hasAffiliation = false;
                    hasAddressTag = false;
                }
                if (!hasAffiliationTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t<affiliation>\n");
                    hasAffiliationTag = true;
                    hasAddressTag = false;
                }

                if (!hasAddressTag) {
                    bufferAffiliation.append("\t\t\t\t\t\t\t<address>\n");
                    hasAddressTag = true;
                }

                bufferAffiliation.append(output);
                lastTag = s1;
                continue;
            }
            if (output != null) {
                if (bufferAffiliation.length() > 0) {
                    if (bufferAffiliation.charAt(bufferAffiliation.length() - 1) == '\n') {
                        bufferAffiliation.deleteCharAt(bufferAffiliation.length() - 1);
                    }
                }
                bufferAffiliation.append(output);
            }
            lastTag = s1;
        }

        if (lastTag != null) {
            if (lastTag.startsWith("I-")) {
                lastTag0 = lastTag.substring(2, lastTag.length());
            } else {
                lastTag0 = lastTag;
            }
            currentTag0 = "";
            testClosingTag(bufferAffiliation, currentTag0, lastTag0);
            if (hasAddressTag) {
                bufferAffiliation.append("\t\t\t\t\t\t\t</address>\n");
            }
            bufferAffiliation.append("\t\t\t\t\t\t</affiliation>\n");
        }

        return bufferAffiliation;
    }

    private String writeField(String s1,
                              String lastTag0,
                              String s2,
                              String field,
                              String outField,
                              boolean addSpace,
                              int nbIndent) {
        String result = null;
        if ((s1.equals(field)) || (s1.equals("I-" + field))) {
            if ((s1.equals("<other>") || s1.equals("I-<other>"))) {
                //result = "";
                /*for(int i=0; i<nbIndent; i++) {
                        result += "\t";
                    }*/
                if (addSpace)
                    result = " " + s2;
                else
                    result = s2;
            } else if (s1.equals(lastTag0) || s1.equals("I-" + lastTag0)) {
                if (addSpace)
                    result = " " + s2;
                else
                    result = s2;
            } else {
                result = "";
                for (int i = 0; i < nbIndent; i++) {
                    result += "\t";
                }
                result += outField + s2;
            }
        }
        return result;
    }

    private boolean testClosingTag(StringBuffer buffer,
                                   String currentTag0,
                                   String lastTag0) {
        boolean res = false;
        if (!currentTag0.equals(lastTag0)) {
            res = true;
            // we close the current tag
            if (lastTag0.equals("<institution>")) {
                buffer.append("</orgName>\n");
            } else if (lastTag0.equals("<department>")) {
                buffer.append("</orgName>\n");
            } else if (lastTag0.equals("<laboratory>")) {
                buffer.append("</orgName>\n");
            } else if (lastTag0.equals("<addrLine>")) {
                buffer.append("</addrLine>\n");
            } else if (lastTag0.equals("<postCode>")) {
                buffer.append("</postCode>\n");
            } else if (lastTag0.equals("<postBox>")) {
                buffer.append("</postBox>\n");
            } else if (lastTag0.equals("<region>")) {
                buffer.append("</region>\n");
            } else if (lastTag0.equals("<settlement>")) {
                buffer.append("</settlement>\n");
            } else if (lastTag0.equals("<country>")) {
                buffer.append("</country>\n");
            } else if (lastTag0.equals("<marker>")) {
                buffer.append("</marker>\n");
            } else if (lastTag0.equals("<other>")) {
                buffer.append("\n");
            } else {
                res = false;
            }
        }
        return res;
    }
}