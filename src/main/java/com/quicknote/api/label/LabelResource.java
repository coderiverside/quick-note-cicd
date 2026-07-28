package com.quicknote.api.label;

import java.net.URI;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.quicknote.api.common.pagination.PageRequest;
import com.quicknote.api.common.pagination.PageResponse;
import com.quicknote.api.label.dto.LabelCreateRequest;
import com.quicknote.api.label.dto.LabelResponse;
import com.quicknote.api.label.dto.LabelUpdateRequest;

/** Label endpoints (contracts/openapi.yaml). */
@Path("/api/v1/labels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LabelResource {

    @Inject
    LabelService labelService;

    @ConfigProperty(name = "quicknote.pagination.default-size")
    int defaultPageSize;

    @ConfigProperty(name = "quicknote.pagination.max-size")
    int maxPageSize;

    @POST
    public Response create(@Valid LabelCreateRequest request) {
        LabelResponse created = labelService.create(request == null ? new LabelCreateRequest(null) : request);
        return Response.created(URI.create("/api/v1/labels/" + created.id())).entity(created).build();
    }

    @GET
    public PageResponse<LabelResponse> list(@QueryParam("page") Integer page, @QueryParam("size") Integer size) {
        return labelService.list(PageRequest.of(page, size, defaultPageSize, maxPageSize));
    }

    @PATCH
    @Path("/{labelId}")
    public LabelResponse rename(@PathParam("labelId") UUID labelId, @Valid LabelUpdateRequest request) {
        return labelService.rename(labelId, request == null ? new LabelUpdateRequest(null) : request);
    }

    @DELETE
    @Path("/{labelId}")
    public Response delete(@PathParam("labelId") UUID labelId) {
        labelService.delete(labelId);
        return Response.noContent().build();
    }
}
