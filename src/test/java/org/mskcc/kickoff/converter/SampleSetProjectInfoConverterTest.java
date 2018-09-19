package org.mskcc.kickoff.converter;

import org.apache.commons.lang3.StringUtils;
import org.hamcrest.object.IsCompatibleType;
import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.RequestSpecies;
import org.mskcc.domain.SampleSet;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.KickoffSampleSet;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.sampleset.SampleSetProjectInfoConverter;
import org.mskcc.kickoff.sampleset.SampleSetToRequestConverter;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.ConverterUtils;
import org.mskcc.util.TestUtils;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;

public class SampleSetProjectInfoConverterTest {
    private final ProcessingType processingType = mock(ProcessingType.class);
    private final String primaryReqId = "primaryReqId";
    private SampleSetProjectInfoConverter sampleSetProjectInfoConverter = new SampleSetProjectInfoConverter();
    private KickoffRequest primaryKickoffRequest;
    private KickoffRequest kickoffRequest;
    private Random random = new Random();
    private KickoffSampleSet sampleSet;

    @Before
    public void setUp() throws Exception {
        sampleSet = new KickoffSampleSet("32113");
        primaryKickoffRequest = getRequestWithRequiredProperties(primaryReqId);
        kickoffRequest = getRequestWithRequiredProperties("213123");
    }

    @Test
    public void whenPrimeRequestHasNoLabHeadSet_shouldThrowAnException() {
        assertExceptionThrownOnMissingPrimaryRequestProperty(Constants.ProjectInfo.LAB_HEAD);
    }

    @Test
    public void whenConvertingProjectInfo_shouldAssignRequestorFromPrimeRequest() {
        assertPropertyIsResolvedFromPrimeRequest(Constants.ProjectInfo.REQUESTOR);
    }

    @Test
    public void whenConvertingProjectInfo_shouldAssignRequestorEmailFromPrimeRequest() {
        assertPropertyIsResolvedFromPrimeRequest(Constants.ProjectInfo.REQUESTOR_E_MAIL);
    }

    @Test
    public void whenConvertingProjectInfo_shouldAssignAlternateEmailsFromPrimeRequest() {
        assertPropertyIsResolvedFromPrimeRequest(Constants.ProjectInfo.ALTERNATE_EMAILS);
    }

    @Test
    public void whenPrimaryRequestHasNoFinalProjectTitle_shouldThrowAnException() {
        assertExceptionThrownOnMissingPrimaryRequestProperty(Constants.ProjectInfo.FINAL_PROJECT_TITLE);
    }

    @Test
    public void whenPrimaryRequestHasNoCmoProjectBrief_shouldThrowAnException() {
        assertExceptionThrownOnMissingPrimaryRequestProperty(Constants.ProjectInfo.CMO_PROJECT_BRIEF);
    }

    @Test
    public void whenPrimaryRequestHasNoProjectManager_shouldThrowAnException() {
        assertExceptionThrownOnMissingPrimaryRequestProperty(Constants.ProjectInfo.PROJECT_MANAGER);
    }

    @Test
    public void whenPrimaryRequestHasNoProjectManagerEmail_shouldThrowAnException() {
        assertExceptionThrownOnMissingPrimaryRequestProperty(Constants.ProjectInfo.PROJECT_MANAGER_EMAIL);
    }

    @Test
    public void whenSampleSetHasDifferentReadmeInfo_shouldEmailBeMergedInRequest() {
        assertPropertyIsMerged(Constants.ProjectInfo.README_INFO);
    }

    @Test
    public void whenConvertingProjectInfo_shouldAssignDataAnalystFromPrimeRequest() {
        assertPropertyIsResolvedFromPrimeRequest(Constants.ProjectInfo.DATA_ANALYST);
    }

    @Test
    public void whenConvertingProjectInfo_shouldAssignDataAnalystEmailFromPrimeRequest() {
        assertPropertyIsResolvedFromPrimeRequest(Constants.ProjectInfo.DATA_ANALYST_EMAIL);
    }

    @Test
    public void whenRequestsHaveNoSamples_shouldSampleSetHasEmptyNumberOfSamples() {
        KickoffRequest kickoffRequest = getRequestWithRequiredProperties(primaryReqId);
        KickoffRequest kickoffRequest2 = getRequestWithRequiredProperties("33234");
        KickoffRequest kickoffRequest3 = getRequestWithRequiredProperties("2345232354");

        setRequests(Arrays.asList(kickoffRequest, kickoffRequest2, kickoffRequest3));
        sampleSet.setPrimaryRequestId(primaryReqId);

        Map<String, String> projectInfo = sampleSetProjectInfoConverter.convert(sampleSet);

        assertThat(projectInfo.get(Constants.ProjectInfo.NUMBER_OF_SAMPLES), is(""));
    }

    @Test
    public void whenSampleSetHasRequestsWithSamples_shouldSetHaveNumberOfSamplesEqualToSumOfAll() {
        KickoffRequest kickoffRequest = getRequestWithProperty(primaryReqId, Constants.ProjectInfo.NUMBER_OF_SAMPLES,
                "3");
        KickoffRequest kickoffRequest2 = getRequestWithProperty("1232", Constants.ProjectInfo.NUMBER_OF_SAMPLES, "10");
        KickoffRequest kickoffRequest3 = getRequestWithProperty("75354", Constants.ProjectInfo.NUMBER_OF_SAMPLES, "42");

        setRequests(Arrays.asList(kickoffRequest, kickoffRequest2, kickoffRequest3));
        sampleSet.setPrimaryRequestId(primaryReqId);

        Map<String, String> projectInfo = sampleSetProjectInfoConverter.convert(sampleSet);

        assertThat(projectInfo.get(Constants.ProjectInfo.NUMBER_OF_SAMPLES), is("55"));
    }

    @Test
    public void whenNotAllRequestsHaveSpeciesSet_shouldThrowAnException() {
        assertExceptionThrownOnMissingRequiredRequestProperty(Constants.ProjectInfo.SPECIES);
    }

    @Test
    public void whenRequestsInSampleSetHaveDifferentSpecies_shouldThrowAnException() {
        assertAmbiguousPropertyThrowsAnException(Constants.ProjectInfo.SPECIES);
    }

    @Test
    public void whenPrimaryRequestHasNoBioinformaticRequest_shouldThrowAnException() {
        assertExceptionThrownOnMissingPrimaryRequestProperty(Constants.ProjectInfo.BIOINFORMATIC_REQUEST);
    }

    @Test
    public void whenProjectHasOneRequestWhichIsPrimary_shouldPropertiesShouldBeTakenFromThisRequest() {
        List<KickoffRequest> requests = Arrays.asList(primaryKickoffRequest);
        setRequests(requests);
        sampleSet.setPrimaryRequestId(primaryReqId);
        Map<String, String> projectInfo = sampleSetProjectInfoConverter.convert(sampleSet);

        assertProjectInfo(projectInfo);
    }

    private void setRequests(List<KickoffRequest> requests) {
        sampleSet.setKickoffRequests(requests);

        for (KickoffRequest request : requests) {
            sampleSet.putRequestIfAbsent(request);
        }
    }

    @Test
    public void whenProjectHasTwoRequestsAndOneIsPrimary_shouldPropertiesBeTakenFromPrimaryRequest() {
        setRequests(Arrays.asList(primaryKickoffRequest, kickoffRequest));
        sampleSet.setPrimaryRequestId(primaryReqId);

        Map<String, String> projectInfo = sampleSetProjectInfoConverter.convert(sampleSet);

        assertProjectInfo(projectInfo);
    }

    @Test
    public void whenPrimaryRequestNotSet_shouldThrowAnException() {
        setRequests(Arrays.asList(getRequestWithRequiredProperties(primaryReqId)));
        Optional<Exception> exception = TestUtils.assertThrown(() -> sampleSetProjectInfoConverter.convert(sampleSet));

        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), IsCompatibleType.typeCompatibleWith(SampleSetProjectInfoConverter
                .PrimaryRequestNotSetException.class));
    }

    @Test
    public void whenPrimaryRequestNotPartOfSampleSet_shouldThrowAnException() {
        setRequests(Arrays.asList(getRequestWithRequiredProperties("otherId")));
        sampleSet.setKickoffRequests(Arrays.asList(getRequestWithRequiredProperties("otherId")));
        sampleSet.setPrimaryRequestId(primaryReqId);

        Optional<Exception> exception = TestUtils.assertThrown(() -> sampleSetProjectInfoConverter.convert(sampleSet));

        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), IsCompatibleType.typeCompatibleWith(SampleSet
                .PrimaryRequestNotPartOfSampleSetException.class));
    }

    @Test
    public void whenPrimaryRequestHasNoCmoProjectId_shouldThrowAnException() {
        assertExceptionThrownOnMissingPrimaryRequestProperty(Constants.ProjectInfo.CMO_PROJECT_ID);
    }

    private void assertPropertyIsResolvedFromPrimeRequest(String property) {
        String primeRequestValue = "someValue";
        KickoffRequest request1 = getRequestWithProperty(primaryReqId, property, primeRequestValue);
        KickoffRequest request2 = getRequestWithProperty("someId", property, "someOtherValue");

        setRequests(Arrays.asList(request1, request2));
        sampleSet.setPrimaryRequestId(primaryReqId);

        Map<String, String> projectInfo = sampleSetProjectInfoConverter.convert(sampleSet);

        assertThat(projectInfo.get(property), is(primeRequestValue));
    }

    private void assertArbitraryPropertySet(String arbitraryProperty) {
        String arbitraryValue = "someValue";
        KickoffRequest request1 = getRequestWithProperty(primaryReqId, arbitraryProperty, arbitraryValue);
        KickoffRequest request2 = getRequestWithProperty("someId", arbitraryProperty, "someOtherValue");

        setRequests(Arrays.asList(request1, request2));
        sampleSet.setPrimaryRequestId(primaryReqId);

        Map<String, String> projectInfo = sampleSetProjectInfoConverter.convert(sampleSet);

        assertThat(projectInfo.get(arbitraryProperty), is(arbitraryValue));
    }

    private void assertAmbiguousPropertyThrowsAnException(String ambiguousProperty) {
        KickoffRequest request1 = getRequestWithRequiredProperties(primaryReqId);
        request1.addProjectProperty(ambiguousProperty, "someValue");

        KickoffRequest request2 = getRequestWithRequiredProperties("5678");
        request2.addProjectProperty(ambiguousProperty, "otherValue");

        setRequests(Arrays.asList(request1, request2));
        sampleSet.setPrimaryRequestId(primaryReqId);

        Optional<Exception> exception = TestUtils.assertThrown(() -> sampleSetProjectInfoConverter.convert(sampleSet));

        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), IsCompatibleType.typeCompatibleWith(SampleSetToRequestConverter
                .AmbiguousPropertyException.class));
    }

    private void assertPropertyIsMerged(String propertyToMerge) {
        String value1 = "fdfds";
        KickoffRequest request1 = getRequestWithProperty(primaryReqId, propertyToMerge, value1);

        String value2 = "dsdsad";
        KickoffRequest request2 = getRequestWithProperty("4342", propertyToMerge, value2);

        String value3 = "ffddf";
        KickoffRequest request3 = getRequestWithProperty("43424", propertyToMerge, value3);

        setRequests(Arrays.asList(request1, request2, request3));
        sampleSet.setPrimaryRequestId(primaryReqId);

        Map<String, String> projetInfo = sampleSetProjectInfoConverter.convert(sampleSet);

        assertThat(projetInfo.get(propertyToMerge), is(String.format("%s,%s,%s", value1, value2, value3)));
    }

    private void assertExceptionThrownOnMissingPrimaryRequestProperty(String missingProperty) {
        assertExceptionThrownOnMissingProperty(
                missingProperty,
                SampleSetProjectInfoConverter.PropertyInPrimaryRequestNotSetException.class,
                Arrays.asList(primaryReqId, "missing1"),
                String.format("Primary request: %s of project: %s has no property: %s set", primaryReqId, sampleSet
                        .getName(), missingProperty));
    }

    private void assertExceptionThrownOnMissingRequiredRequestProperty(String missingProperty) {
        assertExceptionThrownOnMissingProperty(
                missingProperty,
                ConverterUtils.RequiredPropertyNotSetException.class,
                Arrays.asList("missing1", "missing2", "missing3"),
                String.format("Required field: %s not set for requests: [%s]", missingProperty, "missing1, missing2, " +
                        "missing3"));
    }

    private void assertExceptionThrownOnMissingProperty(String missingProperty, Class exceptionClass, List<String>
            missingPropReqId, String message) {
        List<KickoffRequest> requests = new ArrayList<>();
        requests.add(getRequestWithRequiredProperties(primaryReqId));

        for (String missingReqId : missingPropReqId) {
            requests.add(getRequestWithMissingProjectInfoProperty(missingReqId, missingProperty));
        }

        sampleSet.setKickoffRequests(requests);
        sampleSet.setPrimaryRequestId(primaryReqId);

        Optional<Exception> exception = TestUtils.assertThrown(() -> sampleSetProjectInfoConverter.convert(sampleSet));

        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), IsCompatibleType.typeCompatibleWith(exceptionClass));
        assertThat(exception.get().getMessage(), is(message));
    }

    private KickoffRequest getRequestWithMissingProjectInfoProperty(String requestId, String missingProperty) {
        KickoffRequest requestWithMissingProperty = getRequestWithRequiredProperties(requestId);
        requestWithMissingProperty.getProjectInfo().remove(missingProperty);
        return requestWithMissingProperty;
    }

    private KickoffRequest getRequestWithProperty(String reqId, String propertyName, String value) {
        KickoffRequest request = getRequestWithRequiredProperties(reqId);
        request.addProjectProperty(propertyName, value);

        return request;
    }

    private void assertProjectInfo(Map<String, String> projectInfo) {
        for (Map.Entry<String, String> info : projectInfo.entrySet()) {
            if (info.getKey().equals(Constants.ProjectInfo.IGO_PROJECT_ID))
                assertThat(Constants.ProjectInfo.IGO_PROJECT_ID, projectInfo.get(Constants.ProjectInfo
                        .IGO_PROJECT_ID), is(sampleSet.getName()));
            else if (!primaryKickoffRequest.getProjectInfo().containsKey(info.getKey()))
                assertThat("Property " + info.getKey(), StringUtils.isEmpty(info.getValue()), is(true));
            else
                assertThat("Property " + info.getKey(), info.getValue(), is(primaryKickoffRequest.getProjectInfo()
                        .get(info.getKey())));
        }
    }

    private KickoffRequest getRequestWithRequiredProperties(String requestId) {
        KickoffRequest kickoffRequest = new KickoffRequest(requestId, processingType);
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.LAB_HEAD, "labHead");
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.LAB_HEAD_E_MAIL, "email");
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.SPECIES, RequestSpecies.XENOGRAFT.getValue());
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.CMO_PROJECT_ID, getRandomValue());
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.CMO_PROJECT_BRIEF, getRandomValue());
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.PROJECT_MANAGER, getRandomValue());
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.PROJECT_MANAGER_EMAIL, getRandomValue());
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.FINAL_PROJECT_TITLE, getRandomValue());
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.BIOINFORMATIC_REQUEST, getRandomValue());
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.TUMOR_TYPE, "tumorType");
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.REQUESTOR, "requestor");
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.REQUESTOR_E_MAIL, "requestor@example.com");
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.ALTERNATE_EMAILS, "some@example.com");
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.DATA_ANALYST, "Some Analyst");
        kickoffRequest.addProjectProperty(Constants.ProjectInfo.DATA_ANALYST_EMAIL, "analust@example.com");

        return kickoffRequest;
    }


    private String getRandomValue() {
        return String.valueOf(random.nextInt());
    }
}