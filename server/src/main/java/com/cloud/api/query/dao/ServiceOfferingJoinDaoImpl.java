// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.api.query.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cloud.dc.VsphereStoragePolicyVO;
import com.cloud.dc.dao.VsphereStoragePolicyDao;
import com.cloud.user.AccountManager;
import com.cloud.utils.db.TransactionLegacy;
import org.apache.cloudstack.annotation.AnnotationService;
import org.apache.cloudstack.annotation.dao.AnnotationDao;
import com.cloud.storage.DiskOfferingVO;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.response.ServiceOfferingResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.api.ApiDBUtils;
import com.cloud.api.query.vo.ServiceOfferingJoinVO;
import com.cloud.offering.ServiceOffering;
import com.cloud.server.ResourceTag.ResourceObjectType;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

import javax.inject.Inject;

@Component
public class ServiceOfferingJoinDaoImpl extends GenericDaoBase<ServiceOfferingJoinVO, Long> implements ServiceOfferingJoinDao {
    public static final Logger s_logger = Logger.getLogger(ServiceOfferingJoinDaoImpl.class);

    @Inject
    VsphereStoragePolicyDao _vsphereStoragePolicyDao;
    @Inject
    private AnnotationDao annotationDao;
    @Inject
    private AccountManager accountManager;

    private SearchBuilder<ServiceOfferingJoinVO> sofIdSearch;

    /**
     * Constant used to convert GB into Bytes (or the other way around).
     * GB   *  MB  *  KB  = Bytes //
     * 1024 * 1024 * 1024 = 1073741824
     */
    private static final long GB_TO_BYTES = 1073741824;


    private static final String LIST_DOMAINS_OF_SERVICE_OFFERINGS_USED_BY_DOMAIN_PATH = "SELECT sov.domain_id, \n" +
            "            GROUP_CONCAT('Offering:', vm.uuid) \n" +
            "            FROM   cloud.service_offering_view AS sov\n" +
            "            INNER  JOIN cloud.vm_instance AS vm ON (vm.service_offering_id  = sov.id) \n" +
            "            INNER  JOIN cloud.domain AS domain ON (domain.id = vm.domain_id) \n" +
            "            INNER  JOIN cloud.domain AS domain_so ON (domain_so.id = sov.domain_id) \n" +
            "            WHERE  domain.path LIKE ? \n" +
            "            AND    domain_so.path NOT LIKE ? \n " +
            "            AND    vm.removed IS NULL \n" +
            "            GROUP  BY sov.id";

    protected ServiceOfferingJoinDaoImpl() {

        sofIdSearch = createSearchBuilder();
        sofIdSearch.and("id", sofIdSearch.entity().getId(), SearchCriteria.Op.EQ);
        sofIdSearch.done();

        this._count = "select count(distinct service_offering_view.id) from service_offering_view WHERE ";
    }

    @Override
    public List<ServiceOfferingJoinVO> findByDomainId(long domainId) {
        SearchBuilder<ServiceOfferingJoinVO> sb = createSearchBuilder();
        sb.and("domainId", sb.entity().getDomainId(), SearchCriteria.Op.FIND_IN_SET);
        sb.done();

        SearchCriteria<ServiceOfferingJoinVO> sc = sb.create();
        sc.setParameters("domainId", String.valueOf(domainId));
        return listBy(sc);
    }

    @Override
    public ServiceOfferingResponse newServiceOfferingResponse(ServiceOfferingJoinVO offering) {

        ServiceOfferingResponse offeringResponse = new ServiceOfferingResponse();
        offeringResponse.setId(offering.getUuid());
        offeringResponse.setName(offering.getName());
        offeringResponse.setIsSystemOffering(offering.isSystemUse());
        offeringResponse.setDefaultUse(offering.isDefaultUse());
        offeringResponse.setSystemVmType(offering.getSystemVmType());
        offeringResponse.setDisplayText(offering.getDisplayText());
        offeringResponse.setProvisioningType(offering.getProvisioningType().toString());
        offeringResponse.setCpuNumber(offering.getCpu());
        offeringResponse.setCpuSpeed(offering.getSpeed());
        offeringResponse.setMemory(offering.getRamSize());
        offeringResponse.setCreated(offering.getCreated());
        offeringResponse.setStorageType(offering.isUseLocalStorage() ? ServiceOffering.StorageType.local.toString() : ServiceOffering.StorageType.shared.toString());
        offeringResponse.setOfferHa(offering.isOfferHA());
        offeringResponse.setLimitCpuUse(offering.isLimitCpuUse());
        offeringResponse.setVolatileVm(offering.getVolatileVm());
        offeringResponse.setTags(offering.getTags());
        offeringResponse.setDomain(offering.getDomainName());
        offeringResponse.setDomainId(offering.getDomainUuid());
        offeringResponse.setZone(offering.getZoneName());
        offeringResponse.setZoneId(offering.getZoneUuid());
        offeringResponse.setNetworkRate(offering.getRateMbps());
        offeringResponse.setHostTag(offering.getHostTag());
        offeringResponse.setDeploymentPlanner(offering.getDeploymentPlanner());
        offeringResponse.setCustomizedIops(offering.isCustomizedIops());
        offeringResponse.setMinIops(offering.getMinIops());
        offeringResponse.setMaxIops(offering.getMaxIops());
        offeringResponse.setHypervisorSnapshotReserve(offering.getHypervisorSnapshotReserve());
        offeringResponse.setBytesReadRate(offering.getBytesReadRate());
        offeringResponse.setBytesReadRateMax(offering.getBytesReadRateMax());
        offeringResponse.setBytesReadRateMaxLength(offering.getBytesReadRateMaxLength());
        offeringResponse.setBytesWriteRate(offering.getBytesWriteRate());
        offeringResponse.setBytesWriteRateMax(offering.getBytesWriteRateMax());
        offeringResponse.setBytesWriteRateMaxLength(offering.getBytesWriteRateMaxLength());
        offeringResponse.setIopsReadRate(offering.getIopsReadRate());
        offeringResponse.setIopsReadRateMax(offering.getIopsReadRateMax());
        offeringResponse.setIopsReadRateMaxLength(offering.getIopsReadRateMaxLength());
        offeringResponse.setIopsWriteRate(offering.getIopsWriteRate());
        offeringResponse.setIopsWriteRateMax(offering.getIopsWriteRateMax());
        offeringResponse.setIopsWriteRateMaxLength(offering.getIopsWriteRateMaxLength());
        Map<String, String> offeringDetails = ApiDBUtils.getResourceDetails(offering.getId(), ResourceObjectType.ServiceOffering);
        offeringResponse.setDetails(offeringDetails);
        offeringResponse.setObjectName("serviceoffering");
        offeringResponse.setIscutomized(offering.isDynamic());
        offeringResponse.setCacheMode(offering.getCacheMode());
        offeringResponse.setDynamicScalingEnabled(offering.isDynamicScalingEnabled());
        offeringResponse.setEncryptRoot(offering.getEncryptRoot());

        if (offeringDetails != null && !offeringDetails.isEmpty()) {
            String vsphereStoragePolicyId = offeringDetails.get(ApiConstants.STORAGE_POLICY);
            if (vsphereStoragePolicyId != null) {
                VsphereStoragePolicyVO vsphereStoragePolicyVO = _vsphereStoragePolicyDao.findById(Long.parseLong(vsphereStoragePolicyId));
                if (vsphereStoragePolicyVO != null)
                    offeringResponse.setVsphereStoragePolicy(vsphereStoragePolicyVO.getName());
            }
        }

        long rootDiskSizeInGb = (long) offering.getRootDiskSize() / GB_TO_BYTES;
        offeringResponse.setRootDiskSize(rootDiskSizeInGb);
        offeringResponse.setDiskOfferingStrictness(offering.getDiskOfferingStrictness());
        DiskOfferingVO diskOfferingVO = ApiDBUtils.findDiskOfferingById(offering.getDiskOfferingId());
        if (diskOfferingVO != null) {
            offeringResponse.setDiskOfferingId(offering.getDiskOfferingUuid());
            offeringResponse.setDiskOfferingName(offering.getDiskOfferingName());
            offeringResponse.setDiskOfferingDisplayText(offering.getDiskOfferingDisplayText());
        }

        offeringResponse.setHasAnnotation(annotationDao.hasAnnotations(offering.getUuid(), AnnotationService.EntityType.SERVICE_OFFERING.name(),
                accountManager.isRootAdmin(CallContext.current().getCallingAccount().getId())));

        return offeringResponse;
    }

    @Override
    public ServiceOfferingJoinVO newServiceOfferingView(ServiceOffering offering) {
        SearchCriteria<ServiceOfferingJoinVO> sc = sofIdSearch.create();
        sc.setParameters("id", offering.getId());
        List<ServiceOfferingJoinVO> offerings = searchIncludingRemoved(sc, null, null, false);
        assert offerings != null && offerings.size() == 1 : "No service offering found for offering id " + offering.getId();
        return offerings.get(0);
    }


    @Override
    public Map<Long, List<String>> listDomainsOfServiceOfferingsUsedByDomainPath(String domainPath) {
        s_logger.debug(String.format("Retrieving the domains of the service offerings used by domain with path [%s].", domainPath));

        TransactionLegacy txn = TransactionLegacy.currentTxn();
        try (PreparedStatement pstmt = txn.prepareStatement(LIST_DOMAINS_OF_SERVICE_OFFERINGS_USED_BY_DOMAIN_PATH)) {
            Map<Long, List<String>> domainsOfServiceOfferingsUsedByDomainPath = new HashMap<>();

            String domainSearch = domainPath.concat("%");
            pstmt.setString(1, domainSearch);
            pstmt.setString(2, domainSearch);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Long domainId = rs.getLong(1);
                    List<String> vmUuids = Arrays.asList(rs.getString(2).split(","));

                    domainsOfServiceOfferingsUsedByDomainPath.put(domainId, vmUuids);
                }
            }

            return domainsOfServiceOfferingsUsedByDomainPath;
        } catch (SQLException e) {
            s_logger.error(String.format("Failed to retrieve the domains of the service offerings used by domain with path [%s] due to [%s]. Returning an empty "
                    + "list of domains.", domainPath, e.getMessage()));

            s_logger.debug(String.format("Failed to retrieve the domains of the service offerings used by domain with path [%s]. Returning an empty "
                    + "list of domains.", domainPath), e);

            return new HashMap<>();
        }
    }
}
