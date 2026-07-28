package com.quicknote.api.note;

import java.net.URI;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.quicknote.api.common.pagination.PageRequest;
import com.quicknote.api.common.pagination.PageResponse;
import com.quicknote.api.note.dto.ChecklistItemCreateRequest;
import com.quicknote.api.note.dto.ChecklistItemUpdateRequest;
import com.quicknote.api.note.dto.NoteCreateRequest;
import com.quicknote.api.note.dto.NoteResponse;
import com.quicknote.api.note.dto.NoteUpdateRequest;

/**
 * Notes endpoints (contracts/openapi.yaml).
 *
 * <p>Carries no business logic and no ownership checks of its own: it translates
 * HTTP into service calls and back. Authentication and the {@code user}-role RBAC
 * gate are applied globally via {@code quarkus.security.jaxrs.default-roles-allowed}
 * rather than annotated here, so no endpoint can be added unprotected.
 */
@Path("/api/v1/notes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NoteResource {

    @Inject
    NoteService noteService;

    @ConfigProperty(name = "quicknote.pagination.default-size")
    int defaultPageSize;

    @ConfigProperty(name = "quicknote.pagination.max-size")
    int maxPageSize;

    @POST
    public Response create(@Valid NoteCreateRequest request) {
        NoteResponse created =
                noteService.create(request == null ? new NoteCreateRequest(null, null, null, null) : request);
        return Response.created(URI.create("/api/v1/notes/" + created.id())).entity(created).build();
    }

    @GET
    public PageResponse<NoteResponse> list(
            @QueryParam("view") String view,
            @QueryParam("label") UUID labelId,
            @QueryParam("search") String search,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size) {
        return noteService.list(
                NoteView.from(view), labelId, search, PageRequest.of(page, size, defaultPageSize, maxPageSize));
    }

    @GET
    @Path("/{noteId}")
    public NoteResponse get(@PathParam("noteId") UUID noteId) {
        return noteService.get(noteId);
    }

    @PATCH
    @Path("/{noteId}")
    public NoteResponse update(@PathParam("noteId") UUID noteId, @Valid NoteUpdateRequest request) {
        return noteService.update(noteId, request == null ? new NoteUpdateRequest() : request);
    }

    @POST
    @Path("/{noteId}/pin")
    public NoteResponse pin(@PathParam("noteId") UUID noteId) {
        return noteService.setPinned(noteId, true);
    }

    @DELETE
    @Path("/{noteId}/pin")
    public NoteResponse unpin(@PathParam("noteId") UUID noteId) {
        return noteService.setPinned(noteId, false);
    }

    @POST
    @Path("/{noteId}/items")
    public Response addItem(@PathParam("noteId") UUID noteId, @Valid ChecklistItemCreateRequest request) {
        NoteResponse note = noteService.addItem(noteId, request);
        return Response.status(Response.Status.CREATED).entity(note).build();
    }

    @PATCH
    @Path("/{noteId}/items/{itemId}")
    public NoteResponse updateItem(
            @PathParam("noteId") UUID noteId,
            @PathParam("itemId") UUID itemId,
            @Valid ChecklistItemUpdateRequest request) {
        return noteService.updateItem(noteId, itemId, request == null ? new ChecklistItemUpdateRequest() : request);
    }

    @DELETE
    @Path("/{noteId}/items/{itemId}")
    public Response removeItem(@PathParam("noteId") UUID noteId, @PathParam("itemId") UUID itemId) {
        noteService.removeItem(noteId, itemId);
        return Response.noContent().build();
    }

    @PUT
    @Path("/{noteId}/labels/{labelId}")
    public NoteResponse applyLabel(@PathParam("noteId") UUID noteId, @PathParam("labelId") UUID labelId) {
        return noteService.applyLabel(noteId, labelId);
    }

    @DELETE
    @Path("/{noteId}/labels/{labelId}")
    public NoteResponse removeLabel(@PathParam("noteId") UUID noteId, @PathParam("labelId") UUID labelId) {
        return noteService.removeLabel(noteId, labelId);
    }

    @POST
    @Path("/{noteId}/archive")
    public NoteResponse archive(@PathParam("noteId") UUID noteId) {
        return noteService.setArchived(noteId, true);
    }

    @DELETE
    @Path("/{noteId}/archive")
    public NoteResponse unarchive(@PathParam("noteId") UUID noteId) {
        return noteService.setArchived(noteId, false);
    }

    @POST
    @Path("/{noteId}/trash")
    public NoteResponse trash(@PathParam("noteId") UUID noteId) {
        return noteService.trash(noteId);
    }

    @DELETE
    @Path("/{noteId}/trash")
    public NoteResponse restore(@PathParam("noteId") UUID noteId) {
        return noteService.restore(noteId);
    }

    @DELETE
    @Path("/{noteId}")
    public Response deletePermanently(@PathParam("noteId") UUID noteId) {
        noteService.deletePermanently(noteId);
        return Response.noContent().build();
    }
}
